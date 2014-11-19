/*
 * copyright 2014, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.queue;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.lang.Thread.State;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.conf.ServerConf;
import poke.server.managers.ConnectionManager;
import poke.server.managers.ElectionManager;
import poke.server.managers.MetaDataManager;
import poke.server.managers.RoutedJobManager;
import poke.server.managers.RoutingManager;
import poke.server.resources.Resource;
import poke.server.resources.ResourceFactory;
import poke.server.resources.ResourceUtil;
import poke.server.roundrobin.RoundRobinInitilizers;

import com.google.protobuf.GeneratedMessage;

import eye.Comm.Header;
import eye.Comm.Payload;
import eye.Comm.PhotoHeader;
import eye.Comm.PhotoHeader.RequestType;
import eye.Comm.PhotoHeader.ResponseFlag;
import eye.Comm.PhotoPayload;
import eye.Comm.PokeStatus;
import eye.Comm.Request;

/**
 * A server queue exists for each connection (channel). A per-channel queue
 * isolates clients. However, with a per-client model. The server is required to
 * use a master scheduler/coordinator to span all queues to enact a QoS policy.
 * 
 * How well does the per-channel work when we think about a case where 1000+
 * connections?
 * 
 * @author gash
 * 
 */
public class PerChannelQueue implements ChannelQueue {
	protected static Logger logger = LoggerFactory.getLogger("server");

	public Channel channel;
	protected ServerConf conf = new ServerConf();
	private int routeNodeId = -1;
	// The queues feed work to the inbound and outbound threads (workers). The
	// threads perform a blocking 'get' on the queue until a new event/task is
	// enqueued. This design prevents a wasteful 'spin-lock' design for the
	// threads
	private LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> inbound;
	private LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> outbound;

	// This implementation uses a fixed number of threads per channel
	private OutboundWorker oworker;
	private InboundWorker iworker;

	// not the best method to ensure uniqueness
	private ThreadGroup tgroup = new ThreadGroup("ServerQueue-"
			+ System.nanoTime());
	public static int countForReadRsp = 0;
	public static int responseCirBkr = 3;

	protected PerChannelQueue(Channel channel) {
		this.channel = channel;
		init();
	}

	protected void init() {
		inbound = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();
		outbound = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();

		iworker = new InboundWorker(tgroup, 1, this);
		iworker.start();

		oworker = new OutboundWorker(tgroup, 1, this);
		oworker.start();

		// let the handler manage the queue's shutdown
		// register listener to receive closing of channel
		// channel.getCloseFuture().addListener(new CloseListener(this));
	}

	protected Channel getChannel() {
		return channel;
	}

	public void setRouteNodeId(int routeNodeId) {
		this.routeNodeId = routeNodeId;
	}

	public int getRouteNodeId() {
		return this.routeNodeId;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see poke.server.ChannelQueue#shutdown(boolean)
	 */
	@Override
	public void shutdown(boolean hard) {

		channel = null;

		if (hard) {
			// drain queues, don't allow graceful completion
			inbound.clear();
			outbound.clear();
		}

		if (iworker != null) {
			iworker.forever = false;
			if (iworker.getState() == State.BLOCKED
					|| iworker.getState() == State.WAITING)
				iworker.interrupt();
			iworker = null;
		}

		if (oworker != null) {
			oworker.forever = false;
			if (oworker.getState() == State.BLOCKED
					|| oworker.getState() == State.WAITING)
				oworker.interrupt();
			oworker = null;
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see poke.server.ChannelQueue#enqueueRequest(eye.Comm.Finger)
	 */
	@Override
	public void enqueueRequest(Request req, Channel notused) {
		try {
			inbound.put(req);
		} catch (InterruptedException e) {
			logger.error("message not enqueued for processing", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see poke.server.ChannelQueue#enqueueResponse(eye.Comm.Response)
	 */
	@Override
	public void enqueueResponse(Request reply, Channel notused) {
		if (reply == null)
			return;

		try {
			outbound.put(reply);
		} catch (InterruptedException e) {
			logger.error("message not enqueued for reply", e);
		}
	}

	protected class OutboundWorker extends Thread {
		int workerId;
		PerChannelQueue sq;
		boolean forever = true;

		public OutboundWorker(ThreadGroup tgrp, int workerId, PerChannelQueue sq) {
			super(tgrp, "outbound-" + workerId);
			this.workerId = workerId;
			this.sq = sq;

			if (outbound == null)
				throw new RuntimeException(
						"connection worker detected null queue");
		}

		@Override
		public void run() {
			Channel conn = sq.channel;
			if (conn == null || !conn.isOpen()) {
				PerChannelQueue.logger
						.error("connection missing, no outbound communication");
				return;
			}

			while (true) {
				if (!forever && sq.outbound.size() == 0)
					break;

				try {
					// block until a message is enqueued
					GeneratedMessage msg = sq.outbound.take();
					if (responseCirBkr > 0) {
						if (conn.isWritable()) {
							boolean rtn = false;
							if (channel != null && channel.isOpen()
									&& channel.isWritable()) {
								ChannelFuture cf = channel.writeAndFlush(msg);
								//logger.info("I am OUTBOUND WORKER");
								// blocks on write - use listener to be async
								if (cf.isDone()) {
									logger.info("done");
								}
								cf.awaitUninterruptibly();
								rtn = cf.isSuccess();
								if (!rtn) {
									sq.outbound.putFirst(msg);
									responseCirBkr--;
								}
								else {
									//close channel on success
									logger.info("Reply Success!!");
									//channel.close();
								}
							}

						} else {
							sq.outbound.putFirst(msg);
							responseCirBkr--;
							
						}

					} else {
						System.out
								.println("Discard Message --- Circuit Breaker Pattern");
					}

				} catch (InterruptedException ie) {
					break;
				} catch (Exception e) {
					PerChannelQueue.logger.error(
							"Unexpected communcation failure", e);
					break;
				}
			}

			if (!forever) {
				PerChannelQueue.logger.info("connection queue closing");
			}
		}
	}

	protected class InboundWorker extends Thread {
		int workerId;
		PerChannelQueue sq;
		boolean forever = true;

		public InboundWorker(ThreadGroup tgrp, int workerId, PerChannelQueue sq) {
			super(tgrp, "inbound-" + workerId);
			this.workerId = workerId;
			this.sq = sq;

			if (outbound == null)
				throw new RuntimeException(
						"connection worker detected null queue");
		}

		// Check if the node is Leader
		public boolean isLeader() {
			logger.info("Check if leader :: "+ ElectionManager.getInstance().whoIsTheLeader());
			return ElectionManager.getInstance().whoIsTheLeader()
					.equals(getMyNode());
		}

		// get node details
		public int getMyNode() {
			return ResourceFactory.getCfg().getNodeId();
		}

		// process job request
		public void processJob(Request req) {
			// handle it locally
			if (req.getHeader() == null) {
				logger.error("header null");
			}
			Resource rsc = ResourceFactory.getInstance().resourceInstance(req.getHeader());
			Request reply = null;
			if (rsc == null) {
				logger.error("failed to obtain resource for " + req);
				reply = ResourceUtil.buildError(req.getHeader(),
						PokeStatus.NORESOURCE, "Request not processed");
			} else {
				reply = rsc.process(req);
			}
			sq.enqueueResponse(reply, null);

		}

		@Override
		public void run() {
			Channel conn = sq.channel;
			if (conn == null || !conn.isOpen()) {
				PerChannelQueue.logger
						.error("connection missing, no inbound communication");
				return;
			}

			while (true) {
				if (!forever && sq.inbound.size() == 0)
					break;

				try {
					// block until a message is enqueued
					GeneratedMessage msg = sq.inbound.take();

					// process request and enqueue response
					if (msg instanceof Request) {
						Request req = ((Request) msg);
						logger.info("REQUEST Message:: " + req.toString());
						MetaDataManager metaDataMgr = new MetaDataManager();
						logger.info(" total number of node connection " + ConnectionManager.getNumMgmtConnections());
						if(req.getHeader().getRoutingId().toString().equals("PING")){
							//logger.info("ping to calculate response time");
							sq.enqueueResponse(req, conn);
							continue;
						}
						if (ConnectionManager.getNumMgmtConnections() == 0) {
	
							logger.info("Standalone System");
							logger.info("Request processed by " + getMyNode());
							
							if (req.getHeader().getPhotoHeader().getRequestType() == RequestType.write) {	
								UUID imageId = UUID.randomUUID();
								req = setImageUUIDToReq(req, imageId.toString());
								processJob(req);
								setMetaData(metaDataMgr,  imageId, getMyNode());
							} else if (req.getHeader().getPhotoHeader().getRequestType() == RequestType.read){
								processJob(req);
							}else if(req.getHeader().getPhotoHeader().getRequestType() == RequestType.delete){
								processJob(req);
								deleteMetaData(metaDataMgr,req.getBody().getPhotoPayload().getUuid().toString());
							}
						} else if (isLeader()) {
							UUID uniqueJobId = UUID.randomUUID();
							Request.Builder rb = Request.newBuilder();

							Header.Builder header = req.getHeader().toBuilder();
							header.setLeaderId(ResourceFactory.getCfg().getNodeId());
							header.setUniqueJobId(uniqueJobId.toString());

							rb.setHeader(header);
							rb.setBody(req.getBody());

							req = rb.build();
							
							if (req.getHeader().getPhotoHeader().getRequestType() == RequestType.read) {

								int routingNodeId = getMetaData(metaDataMgr, req.getBody().getPhotoPayload().getUuid());
								logger.info("Image location: "+ routingNodeId);
								
								if (routingNodeId != -1 && routingNodeId != getMyNode()) {
									
									logger.info("Request is routed to Node: "+routingNodeId);
									
									if(!RoutingManager.getInstance().getActiveNodeList().contains(routingNodeId)){
											logger.error("failed to obtain resource for this request: " + req);
											Request errReply = ResourceUtil.buildErrMsg();
											sq.enqueueResponse(errReply, null);
											break;
									}
								
									
									RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(routingNodeId);
									rri.addJobsInQueue();
									RoutedJobManager.getInstance().putJob(uniqueJobId.toString(), sq);
									
									//Get existing channel with worker node
									Channel channel = ConnectionManager.getConnection(routingNodeId, false);
									channel.writeAndFlush(req);
									
									RoutedJobManager.getInstance().putJob(uniqueJobId.toString(), sq);
									setRouteNodeId(routingNodeId);
									
								} else if (routingNodeId != -1 && routingNodeId == getMyNode()) {
									RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(routingNodeId);
									rri.addJobsInQueue();
									
									processJob(req);
									
									rri.reduceJobsInQueue();
									
								} else {
									retrivalImageFailure(req, rb, header);
								}
							} else if (req.getHeader().getPhotoHeader().getRequestType() == RequestType.write) {
								
								if((req.getBody().getPhotoPayload().getData().size()/1024) > 56){
									logger.info("Reject request/ file size not permitted");
									sq.enqueueResponse(ResourceUtil.buildErrorMsgCommon(req), null);
									continue;
								}
								UUID imageId = UUID.randomUUID();
								req = setImageUUIDToReq(req, imageId.toString());
								int routedNodeId = RoutingManager.getInstance().routeJobs(getMyNode());
								setMetaData(metaDataMgr,  imageId, routedNodeId);
								
								if (routedNodeId == getMyNode()) {
									logger.info(" Request will be processed by leader Node: "+routedNodeId);
									processJob(req);
									RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(routedNodeId);
									rri.reduceJobsInQueue();
								} else {
									
									if(!RoutingManager.getInstance().getActiveNodeList().contains(routedNodeId)){
										sq.inbound.put(msg);
										continue;
									}
									
									//Get existing channel with worker node
									Channel channel = ConnectionManager.getConnection(routedNodeId, false);
									channel.writeAndFlush(req);
									
									RoutedJobManager.getInstance().putJob(uniqueJobId.toString(), sq);
									
									setRouteNodeId(routedNodeId);
								}
							} else if (req.getHeader().getPhotoHeader().getRequestType() == RequestType.delete){
								int routingNodeId = getMetaData(metaDataMgr, req.getBody().getPhotoPayload().getUuid());
								logger.info("Image location for delete: "+ routingNodeId);
								
								if (routingNodeId != -1 && routingNodeId != getMyNode()) {
									
									logger.info("Request is routed to Node: "+routingNodeId);
									
									if(!RoutingManager.getInstance().getActiveNodeList().contains(routingNodeId)){
											logger.error("failed to obtain resource for this request: " + req);
											Request errReply = ResourceUtil.buildErrMsg();
											sq.enqueueResponse(errReply, null);
											break;
									}
								
									
									RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(routingNodeId);
									rri.addJobsInQueue();
									RoutedJobManager.getInstance().putJob(uniqueJobId.toString(), sq);
									
									//Get existing channel with worker node
									Channel channel = ConnectionManager.getConnection(routingNodeId, false);
									channel.writeAndFlush(req);

									deleteMetaData(metaDataMgr,req.getBody().getPhotoPayload().getUuid().toString());
									
								} else if (routingNodeId != -1 && routingNodeId == getMyNode()) {
									RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(routingNodeId);
									rri.addJobsInQueue();
									
									processJob(req);
									
									rri.reduceJobsInQueue();
									
									deleteMetaData(metaDataMgr,req.getBody().getPhotoPayload().getUuid().toString());
								} else {
									//build image failure messgae
									retrivalImageFailure(req, rb, header);
								}
							
							}
						} else {
							//check if req has leader value or not
							int leaderId = ElectionManager.getInstance().whoIsTheLeader();
							if (leaderId != req.getHeader().getLeaderId()) {
								logger.info(" Reject request becuase it didnt come to leader");
								logger.info(" leader is Node: "+leaderId);
								logger.info(" Request came from Node: "+req.getHeader().getLeaderId());
								sq.channel.close();
							} else {
								logger.info("worker node is processing request \n");
								processJob(req);
							}
						}
					}
				} catch (InterruptedException ie) {
					break;
				} catch (Exception e) {
					PerChannelQueue.logger.error("Unexpected processing failure", e);
					break;
				}
			}

			if (!forever) {
				PerChannelQueue.logger.info("connection queue closing");
			}
		}

		private void retrivalImageFailure(Request req, Request.Builder rb,
				Header.Builder header) {
			header.setReplyMsg("Error in Retrive image");
			PhotoHeader.Builder phdrBldr = PhotoHeader.newBuilder();
			phdrBldr.setResponseFlag(ResponseFlag.failure);
			header.setPhotoHeader(phdrBldr);
			rb.setHeader(header);
			rb.setBody(req.getBody());
			req = rb.build();
			sq.enqueueResponse(req, null);
		}

		private Request setImageUUIDToReq(Request req, String imageId)
				throws Exception {			
			Request.Builder rqBldr = Request.newBuilder();
			Payload.Builder plBldr = req.getBody().toBuilder();
			
			PhotoPayload.Builder phBldr = plBldr.getPhotoPayload().toBuilder();
			phBldr.setUuid(imageId);
			
			plBldr.setPhotoPayload(phBldr);
			
			rqBldr.setHeader(req.getHeader());
			rqBldr.setBody(plBldr);
			req = rqBldr.build();

			return req;
		}

		private void setMetaData(MetaDataManager metaDataMgr, UUID imageId,
				int nodeId) throws Exception {
			if (metaDataMgr.setNodeLocation(imageId.toString(), nodeId)) {
				logger.debug("Image location is saved.");
			} else {
				logger.error("Failed to stored image location.");
			}
		}

		private int getMetaData(MetaDataManager metaDataMgr, String imageId)
				throws Exception {
			int nodeId = metaDataMgr.getNodeLocation(imageId);
			if (nodeId != -1) {
				logger.debug("Image location obtained.");
			} else {
				logger.error("Failed to obtain image location.");
			}
			return nodeId;
		}
		
		private void deleteMetaData(MetaDataManager metaDataMgr, String imageId) throws Exception {
			if (metaDataMgr.deleteNodeLocation(imageId)) {
				logger.debug("Image location is saved.");
			} else {
				logger.error("Failed to stored image location.");
			}
		}
	}

	public class CloseListener implements ChannelFutureListener {
		private ChannelQueue sq;

		public CloseListener(ChannelQueue sq) {
			this.sq = sq;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			sq.shutdown(true);
		}
	}
}
