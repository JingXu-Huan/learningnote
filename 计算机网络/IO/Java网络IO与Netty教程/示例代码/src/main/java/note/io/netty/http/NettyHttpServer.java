package note.io.netty.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.nio.charset.StandardCharsets;

/**
 * 最小 HTTP/1.1 服务：GET /health 与 POST /echo。
 */
public final class NettyHttpServer {
    private static final int PORT = 9006;
    private static final int MAX_CONTENT_LENGTH = 64 * 1024;

    private NettyHttpServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        MultiThreadIoEventLoopGroup boss =
                new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        MultiThreadIoEventLoopGroup worker =
                new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            ChannelPipeline pipeline = channel.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(
                                    MAX_CONTENT_LENGTH));
                            pipeline.addLast(new HttpContentCompressor());
                            pipeline.addLast(new HttpRequestHandler());
                        }
                    });

            Channel server = bootstrap.bind(PORT).sync().channel();
            System.out.println("Netty HTTP listening on http://localhost:"
                    + PORT);
            server.closeFuture().sync();
        } finally {
            boss.shutdownGracefully().sync();
            worker.shutdownGracefully().sync();
        }
    }

    private static final class HttpRequestHandler
            extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(
                ChannelHandlerContext ctx, FullHttpRequest request) {
            if (!request.decoderResult().isSuccess()) {
                respond(ctx, request, HttpResponseStatus.BAD_REQUEST,
                        "bad request", HttpHeaderValues.TEXT_PLAIN);
                return;
            }

            String path = new QueryStringDecoder(request.uri()).path();
            if (path.equals("/health")) {
                if (!request.method().equals(HttpMethod.GET)) {
                    methodNotAllowed(ctx, request, HttpMethod.GET);
                    return;
                }
                respond(ctx, request, HttpResponseStatus.OK,
                        "{\"status\":\"UP\"}",
                        HttpHeaderValues.APPLICATION_JSON);
                return;
            }

            if (path.equals("/echo")) {
                if (!request.method().equals(HttpMethod.POST)) {
                    methodNotAllowed(ctx, request, HttpMethod.POST);
                    return;
                }
                String body = request.content().toString(StandardCharsets.UTF_8);
                respond(ctx, request, HttpResponseStatus.OK,
                        body, HttpHeaderValues.TEXT_PLAIN);
                return;
            }

            respond(ctx, request, HttpResponseStatus.NOT_FOUND,
                    "not found", HttpHeaderValues.TEXT_PLAIN);
        }

        private static void methodNotAllowed(
                ChannelHandlerContext ctx,
                FullHttpRequest request,
                HttpMethod allowed) {
            FullHttpResponse response = response(
                    HttpResponseStatus.METHOD_NOT_ALLOWED,
                    "method not allowed", HttpHeaderValues.TEXT_PLAIN);
            response.headers().set(HttpHeaderNames.ALLOW, allowed.name());
            writeResponse(ctx, request, response);
        }

        private static void respond(
                ChannelHandlerContext ctx,
                FullHttpRequest request,
                HttpResponseStatus status,
                String body,
                CharSequence contentType) {
            writeResponse(ctx, request, response(status, body, contentType));
        }

        private static FullHttpResponse response(
                HttpResponseStatus status,
                String body,
                CharSequence contentType) {
            ByteBuf content = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, status, content);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE,
                    contentType + "; charset=UTF-8");
            HttpUtil.setContentLength(response, content.readableBytes());
            return response;
        }

        private static void writeResponse(
                ChannelHandlerContext ctx,
                FullHttpRequest request,
                FullHttpResponse response) {
            boolean keepAlive = HttpUtil.isKeepAlive(request);
            if (keepAlive) {
                HttpUtil.setKeepAlive(response, true);
                ctx.writeAndFlush(response)
                        .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            } else {
                ctx.writeAndFlush(response)
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
