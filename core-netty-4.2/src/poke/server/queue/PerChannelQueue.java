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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.client.ClientCommand;
import poke.client.comm.MetaDataManager;
import poke.server.conf.ServerConf;
import poke.server.managers.ConnectionManager;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatData;
import poke.server.managers.HeartbeatManager;
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
									channel.close();
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
			logger.info("Check if leader :: "
					+ ElectionManager.getInstance().whoIsTheLeader());
			return ElectionManager.getInstance().whoIsTheLeader()
					.equals(getMyNode());
		}

		// get node details
		public int getMyNode() {
			logger.info("NODE ID :: " + ResourceFactory.getCfg().getNodeId());
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
			} else
				reply = rsc.process(req);
			// logger.info("reply..."+ reply.toString());
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
						logger.info("REQUEST :: " + req.toString());
						MetaDataManager metaDataMgr = new MetaDataManager();

						logger.info(" total number of node connection " + ConnectionManager.getNumMgmtConnections());
						
						if (ConnectionManager.getNumMgmtConnections() == 0) {
							
							logger.info("single node cluster");
							logger.info("request processed by " + getMyNode());
							
							if (req.getHeader().getPhotoHeader().getRequestType() == RequestType.write) {	
								UUID imageId = UUID.randomUUID();
								req = setImageUUIDToReq(req, imageId.toString());
								setMetaData(metaDataMgr,  imageId, getMyNode());
								processJob(req);
							} else if (req.getHeader().getPhotoHeader().getRequestType() == RequestType.read){
								processJob(req);
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
								logger.info("image location: "+ routingNodeId);
								
								if (routingNodeId != -1 && routingNodeId != getMyNode()) {
									
									String hostname = null;
									int hostport = 0;
									logger.info("req is routed to "+routingNodeId);
									ConcurrentHashMap<Integer, HeartbeatData> incomingHB = HeartbeatManager.getInstance().getIncomingHB();
									
									HeartbeatData hbd = incomingHB.get(routingNodeId);
									if(routingNodeId == hbd.getNodeId()) {
										hostname = hbd.getHost();
										hostport = hbd.getPort();
										logger.info(" Forward reuqest to host address- "+hostname+":"+hostport);
									}
									
									if(hostname == null){
										//if host is not active anymore route this process to other host
										sq.inbound.put(msg);
										continue;
									}
									
									RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(routingNodeId);
									rri.addJobsInQueue();
									logger.info(" current jobs with node "+routingNodeId +" are "+rri.getJobsInQueue());
									RoutedJobManager.getInstance().putJob(uniqueJobId.toString(), sq);
									
									ClientCommand cc = new ClientCommand(hostname, hostport);									
									cc.forwardMsg(req);
	
									RoutedJobManager.getInstance().putJob(uniqueJobId.toString(), sq);
									setRouteNodeId(routingNodeId);
									
								} else if (routingNodeId != -1 && routingNodeId == getMyNode()) {
									RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(routingNodeId);
									rri.addJobsInQueue();
									logger.info(" current jobs with node "+routingNodeId +" are "+rri.getJobsInQueue());
									
									processJob(req);
									
									rri.reduceJobsInQueue();
									
								} else {
									//build image failure messgae
									header.setReplyMsg("Error in Retrive image");
									PhotoHeader.Builder phdrBldr = PhotoHeader.newBuilder();
									phdrBldr.setResponseFlag(ResponseFlag.failure);
									header.setPhotoHeader(phdrBldr);
									rb.setHeader(header);
									rb.setBody(req.getBody());
									req = rb.build();
									sq.enqueueResponse(req, null);
								}
							} else if (req.getHeader().getPhotoHeader().getRequestType() == RequestType.write) {
								UUID imageId = UUID.randomUUID();
								req = setImageUUIDToReq(req, imageId.toString());
								int routedNodeId = RoutingManager.getInstance().routeJobs(getMyNode());
								setMetaData(metaDataMgr,  imageId, routedNodeId);
								
								if (routedNodeId == getMyNode()) {
									logger.info(" req will be processed by leader "+routedNodeId);
									processJob(req);
									RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(routedNodeId);
									rri.reduceJobsInQueue();
									logger.info(" current jobs with node"+routedNodeId +" are "+rri.getJobsInQueue());
								} else {
									String hostname = null;
									int hostport = 0;
									logger.info("req is routed to "+routedNodeId);
									ConcurrentHashMap<Integer, HeartbeatData> incomingHB = HeartbeatManager.getInstance().getIncomingHB();
									
									HeartbeatData hbd = incomingHB.get(routedNodeId);
									if(routedNodeId == hbd.getNodeId()){
										hostname = hbd.getHost();
										hostport = hbd.getPort();
										logger.info(" Forward reuqest to host address- "+hostname+":"+hostport);
									}
									
									if(hostname == null){
										//if host is not active anymore route this process to other host
										sq.inbound.put(msg);
										continue;
									}
									
									ClientCommand cc = new ClientCommand(hostname, hostport);									
									cc.forwardMsg(req);

									RoutedJobManager.getInstance().putJob(uniqueJobId.toString(), sq);
									
									setRouteNodeId(routedNodeId);
								}
							}
						} else {
							//check if req has leader value or not
							if (ElectionManager.getInstance().whoIsTheLeader() != req.getHeader().getLeaderId()) {
								logger.info(" Reject request becuase it didnt come from leader");
								logger.info(" leader is "+ElectionManager.getInstance().whoIsTheLeader());
								logger.info(" request came from "+req.getHeader().getLeaderId());
								sq.channel.close();
							} else {
								logger.info("worker node");
								//logger.info("request from leader node "+msg);
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
