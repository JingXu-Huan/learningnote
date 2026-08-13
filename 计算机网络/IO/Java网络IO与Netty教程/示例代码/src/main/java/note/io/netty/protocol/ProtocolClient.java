package note.io.netty.protocol;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public final class ProtocolClient {
    private ProtocolClient() {
    }

    public static void main(String[] args) throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ProtocolPipeline.install(channel.pipeline());
                            channel.pipeline().addLast(new ClientHandler());
                        }
                    });
            bootstrap.connect("127.0.0.1", 9004)
                    .sync().channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    private static final class ClientHandler
            extends SimpleChannelInboundHandler<Message> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.writeAndFlush(new Message(1, 42, "你好，Netty"));
        }

        @Override
        protected void channelRead0(
                ChannelHandlerContext ctx, Message response) {
            System.out.println(response);
            ctx.close();
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
