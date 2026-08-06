# 六、Netty 入门与第一个 Echo 服务

> 第一次读 Netty 代码请先看：[[00A-Netty新手术语与读代码指南]] 和 [[00B-Netty从0到1案例式入门]]。如果还不熟，先只追踪 Echo 的“字节 -> String -> 响应字节”链路，不要一开始背完整启动模板。

> 白话翻译：本章就是“开一家只会回声的电话店”。服务端先等电话，电话接通后安排处理路线；客户说一句话，店里把同一句话加上 `echo:` 再说回去。

## 6.1 Netty 帮我们封装了什么

初学时先把 Netty 代码压缩成四个动作：

```text
1. 创建线程组
2. 配置 Bootstrap
3. 给每条连接安装 Pipeline
4. bind/connect 后等待关闭
```

所有复杂 API 最终都在服务这四件事。看到陌生代码时，先标出它属于哪一个动作，再看细节。

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

### 逐行读 Echo Handler

| 代码部分 | 它的意思 | 初学者要注意 |
| --- | --- | --- |
| `SimpleChannelInboundHandler<String>` | 此 Handler 只接收上游产出的 `String` 入站消息 | 前面的 `StringDecoder` 决定了这里不是 `ByteBuf` |
| `ctx` | 当前 Handler 的上下文/工作台 | 可取得连接、把消息继续传递、发起写操作 |
| `ctx.channel().remoteAddress()` | 当前客户端的 IP 与端口 | `ctx` 不是连接本身，`ctx.channel()` 才是 |
| `message` | 当前这条完整的业务文本 | 不是 TCP 的原始一段字节 |
| `writeAndFlush(...)` | 写入出站链并立即请求发送 | 返回 `ChannelFuture`，发送失败不是立刻抛到当前代码行 |
| `cause` | 本次异常对象 | 打印后关闭是 Echo 示例的保守策略；生产中应先按异常分类 |

`SimpleChannelInboundHandler<String>` 会在 `channelRead0` 返回后自动处理匹配消息的引用计数。这里的消息是普通 `String`，你不需要、也不应该写 `message.release()`。

### 逐项读服务端启动代码

| 变量/调用 | 做什么 | 为什么需要它 |
| --- | --- | --- |
| `boss` | 接收新 TCP 连接 | 把 accept 与已连接客户端的 IO 分开 |
| `worker` | 驱动每个已连接客户端的读写和默认 Handler | 一个 worker 线程可服务多个连接，不能在它上面阻塞 |
| `bootstrap.group(boss, worker)` | 把两组线程交给服务端启动器 | 参数顺序是 boss 在前、worker 在后 |
| `.channel(NioServerSocketChannel.class)` | 指定用 NIO 的监听 Channel | 这是“监听端口”，不是一条客户端连接 |
| `.childHandler(...)` | 为每个新客户端安装 Pipeline | `initChannel(channel)` 会对每条新连接执行一次 |
| `server` | bind 成功后的监听 Channel | `server.closeFuture()` 等待的是“服务器被关闭”，不是“第一位客户端断开” |

### 第一次运行时应观察什么

1. 服务端只输出一次 `Netty Echo listening...`；每个客户端连入才输出一次 `connected...`。
2. 客户端必须发带 `\n` 的文本。因为当前分帧规则是按行切分，不带换行不会进入 `EchoHandler`。
3. Echo 响应多了一个换行，是为了让对端同样能按行读取。

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
