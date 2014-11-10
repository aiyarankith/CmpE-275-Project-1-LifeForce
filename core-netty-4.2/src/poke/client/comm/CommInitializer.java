package poke.client.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.timeout.IdleStateHandler;

public class CommInitializer extends ChannelInitializer<SocketChannel> {

	protected static Logger logger = LoggerFactory.getLogger("connect");
	CommHandler handler = null;

	public CommInitializer(CommHandler handler) {
		this.handler = handler;
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		logger.info("comm conection l=init....................................");
		ChannelPipeline pipeline = ch.pipeline();
		pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
				67108864, 0, 4, 0, 4));
		pipeline.addLast("protobufDecoder", new ProtobufDecoder(
				eye.Comm.Request.getDefaultInstance()));
		pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
		pipeline.addLast("protobufEncoder", new ProtobufEncoder());
		pipeline.addLast("idleStateHandler", new IdleStateHandler(60, 60, 0));
		pipeline.addLast("handler", handler);
	}
}
