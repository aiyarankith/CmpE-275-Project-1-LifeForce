package poke.server.roundrobin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.managers.RoutingManager;

import com.google.protobuf.GeneratedMessage;

import eye.Comm.Request;

public class ResponseHandler extends SimpleChannelInboundHandler<eye.Comm.Request> {
	protected static Logger logger = LoggerFactory.getLogger("response handler");
	
	
	public ResponseHandler(){
		
	}
	
	public boolean send(GeneratedMessage msg, Channel ch ) throws InterruptedException {
		// TODO a queue is needed to prevent overloading of the socket
		// connection. For the demonstration, we don't need it 

		//logger.info("request message "+msg);
		
		ChannelFuture cf = ch.writeAndFlush((Request)msg);
		
		cf.awaitUninterruptibly();
		logger.info(" "+cf.cause());
		if (cf.isDone() && !cf.isSuccess()) {
			logger.error("failed to poke!");
			return false;
		}
		return true;
	}
	
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, eye.Comm.Request msg) throws Exception {
		long currentTime = System.nanoTime();
		long responseTime = currentTime - msg.getHeader().getTime();
		logger.info("latency for node "+ msg.getBody().getPing().getNumber()+" from node "+msg.getHeader().getOriginator());
		logger.info("(current time)"+currentTime+"-(start time)"+msg.getHeader().getTime()+"="+responseTime);
		RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(msg.getBody().getPing().getNumber());
		rri.setLastAverageResponseTime(responseTime);
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
}
