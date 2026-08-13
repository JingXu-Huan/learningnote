package note.io.netty.chat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.nio.charset.StandardCharsets;

/**
 * 最小聊天室服务端：每条消息广播给当前在线的所有客户端。
 */
public final class NettyChatServer {
    private static final int PORT = 9005;
    private static final ChannelGroup ONLINE =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private NettyChatServer() {
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
                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.addLast(new LineBasedFrameDecoder(1024));
                            pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new ChatHandler());
                        }
                    });

            Channel server = bootstrap.bind(PORT).sync().channel();
            System.out.println("Netty Chat listening on " + PORT);
            server.closeFuture().sync();
        } finally {
            boss.shutdownGracefully().sync();
            worker.shutdownGracefully().sync();
        }
    }

    private static final class ChatHandler
            extends SimpleChannelInboundHandler<String> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ONLINE.add(ctx.channel());
            broadcast("有人加入聊天室："
                    + ctx.channel().remoteAddress() + "\n");
        }

        @Override
        protected void channelRead0(
                ChannelHandlerContext ctx, String message) {
            String text = ctx.channel().remoteAddress()
                    + "：" + message + "\n";
            broadcast(text);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            ONLINE.remove(ctx.channel());
            broadcast("有人离开聊天室\n");
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }

        private static void broadcast(String message) {
            ONLINE.writeAndFlush(message);
        }
    }
}
