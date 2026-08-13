package note.io.netty.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * 浏览器可直接连接的最小 WebSocket 聊天室。
 */
public final class NettyWebSocketServer {
    private static final int PORT = 9007;
    private static final int MAX_FRAME_LENGTH = 64 * 1024;
    private static final ChannelGroup ONLINE =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    private NettyWebSocketServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        MultiThreadIoEventLoopGroup boss =
                new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        MultiThreadIoEventLoopGroup worker =
                new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            WebSocketServerProtocolConfig websocketConfig =
                    WebSocketServerProtocolConfig.newBuilder()
                            .websocketPath("/ws")
                            .maxFramePayloadLength(MAX_FRAME_LENGTH)
                            .handshakeTimeoutMillis(10_000)
                            .build();

            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(
                                    MAX_FRAME_LENGTH));
                            pipeline.addLast(new WebSocketServerProtocolHandler(
                                    websocketConfig));
                            pipeline.addLast(new WebSocketFrameAggregator(
                                    MAX_FRAME_LENGTH));
                            pipeline.addLast(new WebSocketChatHandler());
                        }
                    });

            Channel server = bootstrap.bind(PORT).sync().channel();
            System.out.println("Netty WebSocket listening on ws://localhost:"
                    + PORT + "/ws");
            server.closeFuture().sync();
        } finally {
            ONLINE.close().awaitUninterruptibly();
            boss.shutdownGracefully().sync();
            worker.shutdownGracefully().sync();
        }
    }

    private static final class WebSocketChatHandler
            extends SimpleChannelInboundHandler<TextWebSocketFrame> {
        @Override
        public void userEventTriggered(
                ChannelHandlerContext ctx, Object event) {
            if (event instanceof
                    WebSocketServerProtocolHandler.HandshakeComplete) {
                ONLINE.add(ctx.channel());
                broadcast("有人加入：" + ctx.channel().remoteAddress());
                return;
            }
            ctx.fireUserEventTriggered(event);
        }

        @Override
        protected void channelRead0(
                ChannelHandlerContext ctx, TextWebSocketFrame frame) {
            broadcast(ctx.channel().remoteAddress() + "：" + frame.text());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            boolean wasOnline = ONLINE.remove(ctx.channel());
            if (wasOnline) {
                broadcast("有人离开：" + ctx.channel().remoteAddress());
            }
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }

        private static void broadcast(String message) {
            ONLINE.writeAndFlush(new TextWebSocketFrame(message));
        }
    }
}
