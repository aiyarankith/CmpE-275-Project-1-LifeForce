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
package poke.client.comm;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.client.ClientCommand;
import poke.server.ServerHandler;
import poke.server.managers.ConnectionManager;
import poke.server.managers.RoutedJobManager;
import poke.server.managers.RoutingManager;
import poke.server.queue.PerChannelQueue;
import poke.server.roundrobin.RoundRobinInitilizers;

import com.google.protobuf.GeneratedMessage;

import eye.Comm.PhotoHeader.ResponseFlag;
import eye.Comm.Header;
import eye.Comm.Payload;
import eye.Comm.Request;
import eye.Comm.PhotoHeader.RequestType;

public class CommHandler extends SimpleChannelInboundHandler<eye.Comm.Request> {
	protected static Logger logger = LoggerFactory.getLogger("connect");
	protected ConcurrentMap<String, CommListener> listeners = new ConcurrentHashMap<String, CommListener>();

	public CommHandler() {
	}

	/**
	 * messages pass through this method. We use a blackbox design as much as

	 * possible to ensure we can replace the underlining communication without
	 * affecting behavior.
	 * 
	 * @param msg
	 * @return
	 * @throws InterruptedException 
	 */
	public boolean send(GeneratedMessage msg, Channel ch ) throws InterruptedException {
		// TODO a queue is needed to prevent overloading of the socket
		// connection. For the demonstration, we don't need it 

		ChannelFuture cf = ch.writeAndFlush((Request)msg);

		cf.awaitUninterruptibly();
		logger.info(" "+cf.cause());
		if (cf.isDone() && !cf.isSuccess()) {
			logger.error("failed to poke!");
			return false;
		}
		return true;
	}

	/**
	 * Notification registration. Classes/Applications receiving information
	 * will register their interest in receiving content.
	 * 
	 * Note: Notification is serial, FIFO like. If multiple listeners are
	 * present, the data (message) is passed to the listener as a mutable
	 * object.
	 * 
	 * @param listener
	 */
	public void addListener(CommListener listener) {
		if (listener == null)
			return;

		listeners.putIfAbsent(listener.getListenerID(), listener);
	}

	/**
	 * a message was received from the server. Here we dispatch the message to
	 * the client's thread pool to minimize the time it takes to process other
	 * messages.
	 * 
	 * @param ctx
	 *            The channel the message was received from
	 * @param msg
	 *            The message
	 */
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, eye.Comm.Request msg) throws Exception {

		logger.info("Reply message: \n "+msg);
		String uuid = msg.getHeader().getUniqueJobId();
		ConcurrentHashMap<String, PerChannelQueue> outgoingJOBs = RoutedJobManager.getInstance().getJobMap();
		for(String uuidsample : outgoingJOBs.keySet()){
			if(uuidsample.equals(uuid)){
				PerChannelQueue pq = outgoingJOBs.get(uuid);
				RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(pq.getRouteNodeId());
				if(rri != null){
					rri.reduceJobsInQueue();
				}else{
					logger.info("rri null");
				}
				
				if(pq.channel != null){
					pq.enqueueResponse(msg, pq.channel);
				}else{
					logger.error("could not find channel for current reply");
				}
				outgoingJOBs.remove(uuid);
				RoutedJobManager.getInstance().setJobMap(outgoingJOBs);
				ctx.channel().close();
			}
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		logger.error("Unexpected exception occureed", cause);
		ctx.close();
	}
	
	//Server Timeout Event
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
			throws Exception {
		
		if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                ctx.close();
            } else if (e.state() == IdleState.WRITER_IDLE) {
                ctx.channel().close();
             }
        }
	}
}
