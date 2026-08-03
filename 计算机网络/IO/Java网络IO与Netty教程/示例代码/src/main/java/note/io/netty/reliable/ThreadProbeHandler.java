package note.io.netty.reliable;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public final class ThreadProbeHandler
        extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        print("active", ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
        print("read", ctx);
        ctx.fireChannelRead(message);
    }

    private static void print(
            String event, ChannelHandlerContext ctx) {
        System.out.printf("%s channel=%s thread=%s%n",
                event,
                ctx.channel().id().asShortText(),
                Thread.currentThread().getName());
    }
}

