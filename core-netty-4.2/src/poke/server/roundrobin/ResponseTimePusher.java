package poke.server.roundrobin;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.GeneratedMessage;

import eye.Comm.Request;

public class ResponseTimePusher {
	protected static Logger logger = LoggerFactory.getLogger("response-pusher");
	
	private String host;
	private int port;
	private ChannelFuture channel; 
	private EventLoopGroup group;
	private LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> outbound;
	private ResponseHandler handler;
	private OutboundWorker worker;
	
	public ResponseTimePusher(String host, int port){
		this.host = host;
		this.port = port;
		
		init();
	}
	
	public void release() {
		group.shutdownGracefully();
	}
	
	public void sendMessage(Request req) throws Exception {
		// enqueue ping msg
		outbound.put(req);
	}
	
	private void init() {
		// the queue to support client-side surging
		outbound = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();

		try {
			group = new NioEventLoopGroup();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			handler = new ResponseHandler();
			ResponseInitializer ci = new ResponseInitializer(handler);
			
			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class).handler(ci);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
			b.option(ChannelOption.TCP_NODELAY, true);
			b.option(ChannelOption.SO_KEEPALIVE, true);
			
			logger.info( host+" "+port);
			// Make the connection attempt.
			channel = b.connect(host, port).syncUninterruptibly();
			channel.awaitUninterruptibly(5000l);
		
			// want to monitor the connection to the server s.t. if we loose the
			// connection, we can try to re-establish it.
			ClientClosedListener ccl = new ClientClosedListener(this);
			channel.channel().closeFuture().addListener(ccl);
			
		} catch (Exception ex) {
			logger.error("failed to initialize the client connection", ex);

		}
		
		if (channel != null && channel.isDone() && channel.isSuccess())
			logger.info("connection created successfully");
		else
			logger.info("connection failed");
		// start outbound message processor
		worker = new OutboundWorker(this);
		worker.start();
	}
	
	protected Channel connect() {
		// Start the connection attempt.
		if (channel == null) {
			init();
		}

		if (channel.isDone() && channel.isSuccess())
			return channel.channel();
		else
			throw new RuntimeException("Not able to establish connection to server");
	}
	
	protected class OutboundWorker extends Thread {
		
		ResponseTimePusher conn;
		boolean forever = true;

		public OutboundWorker(ResponseTimePusher conn) {
			this.conn = conn;
			//logger.info("outbound worker started!!!");
			if (conn.outbound == null)
				throw new RuntimeException("connection worker detected null queue");
		}

		@Override
		public void run() {
			Channel ch = conn.connect();
			if (ch == null || !ch.isOpen()) {
				ResponseTimePusher.logger.error("connection missing, no outbound communication");
				return;
			}

			while (true) {
				if (!forever && conn.outbound.size() == 0)
					break;

				try {
					// block until a message is enqueued
					GeneratedMessage msg = conn.outbound.take();
					
					if (ch.isWritable()) {
						ResponseHandler handler = conn.connect().pipeline().get(ResponseHandler.class);
						if (!handler.send(msg, ch)){
						//conn.outbound.putFirst(msg);
						}
					} else{
						conn.outbound.putFirst(msg);
					}
				} catch (InterruptedException ie) {
					break;
				} catch (Exception e) {
					ResponseTimePusher.logger.error("Unexpected communcation failure", e);
					break;
				}
			}

			if (!forever) {
				ResponseTimePusher.logger.info("connection queue closing");
			}
		}
	}

	public static class ClientClosedListener implements ChannelFutureListener {
		ResponseTimePusher cc;
	
		public ClientClosedListener(ResponseTimePusher cc) {
			this.cc = cc;
		}
	
		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			// we lost the connection or have shutdown.
			//logger.info("--------------------------------------------------------netstat");
			// @TODO if lost, try to re-establish the connection
		}
	}
}
