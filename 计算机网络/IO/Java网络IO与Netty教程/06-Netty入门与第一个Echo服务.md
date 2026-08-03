# 六、Netty 入门与第一个 Echo 服务

## 6.1 Netty 帮我们封装了什么

手写 NIO 需要处理 Selector 循环、注册、部分读写、写队列、线程安全和连接状态。Netty 将它们组织为：

```text
Bootstrap：组装和启动
Channel：一条连接或服务端监听端点
EventLoop：驱动 Channel 的 IO 与任务
Pipeline：每条 Channel 的处理链
Handler：处理入站/出站事件
ByteBuf：网络数据容器
Future/Promise：异步结果
```

## 6.2 服务端完整骨架

完整代码：`示例代码/src/main/java/note/io/netty/echo/NettyEchoServer.java`。

```java
EventLoopGroup boss = new NioEventLoopGroup(1);
EventLoopGroup worker = new NioEventLoopGroup();

try {
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(boss, worker)
            .channel(NioServerSocketChannel.class)
            .option(ChannelOption.SO_BACKLOG, 128)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    channel.pipeline()
                            .addLast(new LineBasedFrameDecoder(1024))
                            .addLast(new StringDecoder(StandardCharsets.UTF_8))
                            .addLast(new StringEncoder(StandardCharsets.UTF_8))
                            .addLast(new EchoHandler());
                }
            });

    Channel server = bootstrap.bind(9003).sync().channel();
    server.closeFuture().sync();
} finally {
    boss.shutdownGracefully().sync();
    worker.shutdownGracefully().sync();
}
```

Echo Handler：

```java
public final class EchoHandler
        extends SimpleChannelInboundHandler<String> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("连接建立：" + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(
            ChannelHandlerContext ctx, String message) {
        ctx.writeAndFlush("echo: " + message + System.lineSeparator());
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
```

`LineBasedFrameDecoder` 先解决消息边界，`StringDecoder` 再把完整帧转成 String。顺序反过来会让字符串解码器收到不完整消息。

## 6.3 option 与 childOption

```text
NioServerSocketChannel（监听 Channel）
  -> option(...)
  -> accept
      -> NioSocketChannel（客户端连接）
          -> childOption(...)
          -> childHandler(...)
```

`SO_BACKLOG` 属于监听 Channel；`TCP_NODELAY`、`SO_KEEPALIVE` 通常属于已接收的子 Channel。配错对象可能不生效或抛出警告。

## 6.4 客户端骨架

```java
EventLoopGroup group = new NioEventLoopGroup();
try {
    Bootstrap bootstrap = new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) {
                    channel.pipeline()
                            .addLast(new LineBasedFrameDecoder(1024))
                            .addLast(new StringDecoder(StandardCharsets.UTF_8))
                            .addLast(new StringEncoder(StandardCharsets.UTF_8))
                            .addLast(new ClientHandler());
                }
            });
    Channel channel = bootstrap.connect("127.0.0.1", 9003)
            .sync().channel();
    channel.writeAndFlush("hello\n").sync();
    channel.closeFuture().sync();
} finally {
    group.shutdownGracefully().sync();
}
```

`bind`、`connect`、`writeAndFlush` 和 `close` 都是异步操作。示例在 main 线程用 `sync` 等待结果；绝不能在该 Channel 的 EventLoop 中对自己的 Future 调用阻塞等待。

## 6.5 典型生命周期

```text
handlerAdded
  -> channelRegistered
  -> channelActive
  -> channelRead（0..N 次）
  -> channelReadComplete
  -> channelInactive
  -> channelUnregistered
  -> handlerRemoved
```

异常事件可以在任意阶段出现。不要只在 `channelInactive` 清理业务资源，注册失败和初始化失败也要有兜底路径。

## 6.6 官方 API

- [ServerBootstrap](https://netty.io/4.1/api/io/netty/bootstrap/ServerBootstrap.html)
- [Bootstrap](https://netty.io/4.1/api/io/netty/bootstrap/Bootstrap.html)
- [ChannelInitializer](https://netty.io/4.1/api/io/netty/channel/ChannelInitializer.html)
- [ChannelOption](https://netty.io/4.1/api/io/netty/channel/ChannelOption.html)
- [ChannelFuture](https://netty.io/4.1/api/io/netty/channel/ChannelFuture.html)
- [Netty 官方 4.x 入门](https://netty.io/wiki/user-guide-for-4.x.html)

## 6.7 知识问答

**问：为什么服务端需要 boss 和 worker 两组线程？**

答：boss 主要接受新连接，worker 驱动已建立连接。也可把同一组传给 `group(group)`；两组模型便于隔离 accept 与连接 IO。

**问：`bind().sync()` 把 Netty 变成同步框架了吗？**

答：没有。bind 本身返回 Future，main 线程主动等待只是启动控制；连接 IO 仍由 EventLoop 异步驱动。

**问：为什么不能直接添加 `StringDecoder`？**

答：TCP 没有消息边界。先帧解码、再内容解码，才能保证 Handler 收到一条完整业务消息。

**问：`SO_KEEPALIVE` 等于业务心跳吗？**

答：不等于。TCP keepalive 的默认周期通常较长且平台相关；业务心跳可携带协议语义并使用可控超时。

### 动手题

把 `LineBasedFrameDecoder(1024)` 改成 8，并发送超过 8 字节且不换行的消息，观察 `TooLongFrameException` 和连接关闭策略。

------

上一章：[[05-AIO异步完成模型]]　下一章：[[07-EventLoop与线程模型]]

