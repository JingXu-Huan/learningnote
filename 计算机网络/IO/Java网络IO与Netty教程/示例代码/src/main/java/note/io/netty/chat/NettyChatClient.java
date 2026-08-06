package note.io.netty.chat;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 最小聊天室客户端：主线程读控制台，Netty EventLoop 负责收消息。
 */
public final class NettyChatClient {
    private NettyChatClient() {
    }

    public static void main(String[] args) throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline()
                                    .addLast(new LineBasedFrameDecoder(1024))
                                    .addLast(new StringDecoder(
                                            StandardCharsets.UTF_8))
                                    .addLast(new StringEncoder(
                                            StandardCharsets.UTF_8))
                                    .addLast(new ChatClientHandler());
                        }
                    });

            Channel channel = bootstrap.connect("127.0.0.1", 9005)
                    .sync().channel();
            System.out.println("已连接，直接输入文字并回车；Ctrl+Z 后回车退出。");
            try (BufferedReader console = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = console.readLine()) != null
                        && channel.isActive()) {
                    channel.writeAndFlush(line + "\n").sync();
                }
            } finally {
                channel.close().sync();
            }
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    private static final class ChatClientHandler
            extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(
                ChannelHandlerContext ctx, String message) {
            System.out.println(message);
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
