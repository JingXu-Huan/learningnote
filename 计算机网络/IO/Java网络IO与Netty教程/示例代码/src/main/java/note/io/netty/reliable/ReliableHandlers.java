package note.io.netty.reliable;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import note.io.netty.protocol.Message;

import java.util.concurrent.TimeUnit;

public final class ReliableHandlers {
    private static final int PING = 9;
    private static final int PONG = 10;

    private ReliableHandlers() {
    }

    public static void install(ChannelPipeline pipeline) {
        pipeline.addLast("idle", new IdleStateHandler(
                60, 20, 0, TimeUnit.SECONDS));
        pipeline.addLast("heartbeat", new HeartbeatHandler());
        pipeline.addLast("backpressure", new BackpressureHandler());
    }

    private static final class HeartbeatHandler
            extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(
                ChannelHandlerContext ctx, Object event) {
            if (event instanceof IdleStateEvent idle) {
                if (idle.state() == IdleState.WRITER_IDLE) {
                    ctx.writeAndFlush(new Message(PING, 0, ""));
                    return;
                }
                if (idle.state() == IdleState.READER_IDLE) {
                    ctx.close();
                    return;
                }
            }
            ctx.fireUserEventTriggered(event);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) {
            if (message instanceof Message protocolMessage
                    && protocolMessage.type() == PING) {
                ctx.writeAndFlush(
                        new Message(PONG, protocolMessage.requestId(), ""));
                return;
            }
            if (message instanceof Message protocolMessage
                    && protocolMessage.type() == PONG) {
                return;
            }
            ctx.fireChannelRead(message);
        }
    }

    private static final class BackpressureHandler
            extends ChannelDuplexHandler {
        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) {
            boolean writable = ctx.channel().isWritable();
            ctx.channel().config().setAutoRead(writable);
            if (writable) {
                ctx.read();
            }
            ctx.fireChannelWritabilityChanged();
        }
    }
}

