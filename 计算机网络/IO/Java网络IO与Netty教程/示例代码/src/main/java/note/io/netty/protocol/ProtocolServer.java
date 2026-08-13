package note.io.netty.protocol;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import note.io.netty.reliable.ReliableHandlers;

public final class ProtocolServer {
    private static final int PORT = 9004;

    private ProtocolServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        NioEventLoopGroup boss = new NioEventLoopGroup(1);
        NioEventLoopGroup worker = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ProtocolPipeline.install(channel.pipeline());
                            ReliableHandlers.install(channel.pipeline());
                            channel.pipeline().addLast(
                                    "requestHandler", new RequestHandler());
                        }
                    });
            Channel server = bootstrap.bind(PORT).sync().channel();
            System.out.println("Protocol server listening on " + PORT);
            server.closeFuture().sync();
        } finally {
            boss.shutdownGracefully().sync();
            worker.shutdownGracefully().sync();
        }
    }

    private static final class RequestHandler
            extends SimpleChannelInboundHandler<Message> {
        @Override
        protected void channelRead0(
                ChannelHandlerContext ctx, Message request) {
            ctx.writeAndFlush(new Message(
                            2,
                            request.requestId(),
                            "echo: " + request.body()))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
