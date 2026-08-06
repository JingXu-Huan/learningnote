# Netty 白话入门：先看故事，再记专业词

> 如果你已经看过术语，但看到一整段 Netty 启动代码仍然不知道“数据从哪里来、要到哪里去”，先读这一章。
>
> 本章只追踪一条主线：**客户端发来的字节，如何经过 Pipeline 变成业务对象；业务对象又如何变回字节发回客户端。**

------

## 0B.0 先别背名词：把 Netty 想成一个接电话的前台

先暂时忘掉 `Channel`、`Pipeline`、`EventLoop` 这些词。想象你开了一家客服店：

1. 店里有一个前台，等着电话打进来。
2. 电话接通后，前台把这条电话交给一名固定的接线员。
3. 接线员先把对方说的话整理成一整句，再交给翻译员。
4. 翻译员把内容交给业务人员。
5. 业务人员写好答复，再经过翻译员变回声音，沿着这条电话线说回去。

Netty 只是把这家“网络客服店”写成了 Java 代码：

| 先记人话 | Netty 名词 | 现在只需要理解什么 |
| --- | --- | --- |
| 一条电话线 | `Channel` | 和一个客户端来回传数据 |
| 接线员 | `EventLoop` | 盯着几条电话，谁有动静就处理谁 |
| 一条加工路线 | `Pipeline` | 数据要依次经过哪些工位 |
| 一个工位 | `Handler` | 只做一件事，如拆开、翻译、业务处理 |
| 装着原始声音的箱子 | `ByteBuf` | 里面是网络收到的 0 和 1 |
| 开店说明书 | `Bootstrap` | 告诉 Netty 用哪些人、监听哪个端口 |
| 当前工位的对讲机 | `ctx` | 从当前工位继续传递或发回结果 |

### 客户端说“你好”时，不要想 API，先想下面 8 步

```text
1. 服务端一直等电话
2. 客户端拨通一条新电话
3. 一个接线员负责这条电话
4. 客户端的“你好”先以 0 和 1 到达
5. Netty 把 0 和 1 装进 ByteBuf 箱子
6. 分帧工位判断“一句话到这里结束了”
7. 翻译工位把字节变成 String，业务工位处理它
8. 业务工位写出答复，Netty 再把答复变成字节发回去
```

所以看到下面这段代码时，先翻译成人话：

```java
pipeline.addLast(new LineBasedFrameDecoder(1024));
pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
pipeline.addLast(new EchoHandler());
```

它不是三个神秘 API，而是三名工作人员：

```text
第一名：找到一句话的结尾
第二名：把字节翻译成 Java 字符串
第三名：按照业务规则处理这句话
```

再看到：

```java
ctx.writeAndFlush("收到啦\n");
```

先翻译成：**当前业务人员写好一句回复，马上沿电话线发出去。**

本章后面的专业词都按照这个方法读：先看它在故事里负责什么，再记它的 Java 名字。

## 0B.1 先只记住五个角色

先不要背几十个 API。把 Netty 想成“负责搬运网络数据的流水线”即可：

| 角色 | 一句话解释 | 生活中的类比 |
| --- | --- | --- |
| `Channel` | 一条网络通道 | 一条电话线；服务端监听端口也有一个 Channel |
| `EventLoop` | 不断检查网络事件并执行回调的线程 | 一个固定服务员，负责几张桌子 |
| `ChannelPipeline` | 一条 Channel 上的处理链 | 一条流水线 |
| `Handler` | 流水线上的一个处理步骤 | 接待、拆包、翻译、业务处理、打包 |
| `ByteBuf` | 网络字节的容器 | 装着原材料的箱子 |

一条最重要的路径是：

```text
客户端
  │ 发送 TCP 字节
  ▼
Channel
  ▼
EventLoop
  ▼
Pipeline 中的入站 Handler
  │  ByteBuf -> 一帧 -> String/Message
  ▼
业务 Handler
  │  String/Message -> 响应
  ▼
Pipeline 中的出站 Handler
  │ String/Message -> ByteBuf
  ▼
Channel
  ▼
客户端
```

这里的“入站”指**从网络进入程序**，“出站”指**从程序发往网络**。

### 先纠正一个常见误解

Netty 不会直接收到 `String`，也不会直接收到 `Message`。最开始收到的只有字节：

```text
网络上：68 65 6C 6C 6F 0A
                 │
                 │ LineBasedFrameDecoder 找到换行符
                 ▼
一帧 ByteBuf：hello
                 │
                 │ StringDecoder 按 UTF-8 解码
                 ▼
业务 String："hello"
```

因此，当你看到：

```java
protected void channelRead0(
        ChannelHandlerContext ctx, String message) {
}
```

不要问“Socket 为什么会给我 String”，应该问：**Pipeline 前面哪个 Handler 把 ByteBuf 变成了 String？**

------

## 0B.2 第一个案例：把 Echo 服务翻译成人话

运行代码：

`示例代码/src/main/java/note/io/netty/echo/NettyEchoServer.java`

这个服务做的事情非常简单：客户端发送一行文字，服务端原样加上 `echo:` 再发回去。

### 第一步：准备两组 EventLoop

```java
NioEventLoopGroup boss = new NioEventLoopGroup(1);
NioEventLoopGroup worker = new NioEventLoopGroup();
```

可以先把它翻译成：

```text
boss：负责“有人打电话进来吗”，也就是接收新连接
worker：负责“这通电话说了什么”，也就是处理已连接客户端的读写
```

`boss` 和 `worker` 不是两个客户端，也不是两个业务模块，而是两组线程。

一个客户端连接进来以后，大致变成这样：

```text
监听端口
NioServerSocketChannel
        │ accept
        ▼
客户端 A 的连接       客户端 B 的连接
NioSocketChannel      NioSocketChannel
        │                     │
        └────── worker EventLoop 管理 ──────┘
```

注意：`NioServerSocketChannel` 是“监听端口”，`NioSocketChannel` 才是“和某一个客户端通信”。

### 第二步：创建启动配置器

```java
ServerBootstrap bootstrap = new ServerBootstrap()
        .group(boss, worker)
        .channel(NioServerSocketChannel.class);
```

`ServerBootstrap` 不是服务器本身，更像“服务器安装说明书”。它记录：

- 用哪两组线程；
- 监听 Channel 使用什么实现；
- 每条新客户端连接使用什么 Pipeline；
- 最后监听哪个端口。

### 第三步：为每个客户端安装 Pipeline

```java
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
```

这段代码的意思不是“只初始化一次服务器”，而是：**以后每来一个客户端，就给这个客户端创建一条这样的处理链。**

四个 Handler 的职责如下：

| 顺序 | Handler | 入站时做什么 | 出站时做什么 |
| --- | --- | --- | --- |
| 1 | `LineBasedFrameDecoder` | 从字节流中找 `\n`，切出完整一行 | 不参与 |
| 2 | `StringDecoder` | `ByteBuf` 转成 `String` | 不参与 |
| 3 | `StringEncoder` | 不参与 | `String` 转成 `ByteBuf` |
| 4 | `EchoHandler` | 处理 `String` | 发起响应 |

入站方向：

```text
网络字节 -> LineBasedFrameDecoder -> StringDecoder -> EchoHandler
```

出站方向相反：

```text
EchoHandler -> StringEncoder -> 网络字节
```

`StringEncoder` 明明写在 `EchoHandler` 前面，为什么响应时还能被它处理？因为 Pipeline 是双向的：入站从前往后，出站从后往前。

### 第四步：绑定端口并等待关闭

```java
Channel server = bootstrap.bind(9003).sync().channel();
System.out.println("Netty Echo listening on 9003");
server.closeFuture().sync();
```

逐句翻译：

1. `bind(9003)`：请求操作系统监听 9003 端口。
2. `.sync()`：在当前 `main` 线程等待绑定结果。
3. `.channel()`：取出绑定成功后的监听 Channel。
4. `closeFuture().sync()`：让 `main` 线程继续等待，避免程序直接退出。

这里的 `sync()` 不代表 Netty 的网络 IO 变成了 BIO。它只是 `main` 线程在等待启动或关闭；真正的客户端读写仍由 EventLoop 异步处理。

### 第五步：读取一条消息并回写

```java
private static final class EchoHandler
        extends SimpleChannelInboundHandler<String> {

    @Override
    protected void channelRead0(
            ChannelHandlerContext ctx, String message) {
        ctx.writeAndFlush("echo: " + message + "\n");
    }
}
```

这里有三个关键点：

1. `<String>` 表示这个 Handler 期待上游传给它 `String`。
2. `message` 是已经去掉换行符的一行文本，不是原始网络字节。
3. `writeAndFlush` 发起出站操作，字符串会向前找到 `StringEncoder`，最后变成网络字节。

一次完整 Echo 的时间线：

```text
客户端调用 writeAndFlush("hello netty\n")
        │
        ▼
服务端收到可能不完整的一段 ByteBuf
        │
        ▼
LineBasedFrameDecoder 等到 \n，得到一整行
        │
        ▼
StringDecoder 得到 "hello netty"
        │
        ▼
EchoHandler 生成 "echo: hello netty\n"
        │
        ▼
StringEncoder 编码为 UTF-8 字节
        │
        ▼
客户端收到响应
```

------

## 0B.3 第二个案例：为什么一定要分帧

TCP 像一条没有分隔线的水管。客户端连续发送：

```text
hello\nworld\n
```

服务端可能收到：

```text
第一次 read：hello\nwo
第二次 read：rld\n
```

也可能收到：

```text
第一次 read：hello\nworld\n
```

还可能收到：

```text
第一次 read：hel
第二次 read：lo\nworld\n
```

这三种情况都正常。`read` 的次数不等于客户端 `write` 的次数。

### 没有分帧器会发生什么

如果 Pipeline 只有：

```java
pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
pipeline.addLast(new EchoHandler());
```

那么 `EchoHandler` 可能拿到：

- 半条消息：`"hel"`；
- 一条半消息：`"hello\\nwo"`；
- 两条消息粘在一起：`"hello\\nworld\\n"`。

这不是 `StringDecoder` 写错了，而是它只负责“字节转字符串”，不负责判断一条业务消息在哪里结束。

### 按换行切帧的案例

```java
pipeline.addLast(new LineBasedFrameDecoder(1024));
pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
pipeline.addLast(new EchoHandler());
```

现在：

- 收到 `hel`：解码器先缓存，不调用 `EchoHandler`；
- 再收到 `lo\n`：拼成 `hello\n`，调用一次 `EchoHandler`；
- 收到 `a\nb\n`：可能连续调用两次 `EchoHandler`；
- 超过 1024 字节仍没有换行：抛出 `TooLongFrameException`。

所以“分帧”就是把不可靠的网络读取次数，转换成可靠的业务消息次数。

------

## 0B.4 第三个案例：直接观察 ByteBuf

前面的 Echo 为了容易理解，把字节很快转换成了 String。下面故意不加 `StringDecoder`，直接看 Netty 最初交给 Handler 的东西：

```java
pipeline.addLast(new LineBasedFrameDecoder(1024));
pipeline.addLast(new ChannelInboundHandlerAdapter() {
    @Override
    public void channelRead(
            ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            String text = buf.toString(StandardCharsets.UTF_8);
            System.out.println("收到字节：" + text);
            ctx.writeAndFlush(
                    Unpooled.copiedBuffer("ok\n", StandardCharsets.UTF_8));
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
});
```

这段代码只用于观察 ByteBuf，读懂四件事即可：

| 代码 | 解释 |
| --- | --- |
| `Object msg` | 此时还没有把数据声明成 String |
| `(ByteBuf) msg` | 把入站对象转换成字节容器 |
| `buf.toString(UTF_8)` | 读取可读字节并按 UTF-8 解码，不是协议分帧 |
| `ReferenceCountUtil.release(msg)` | 当前 Handler 消费完 ByteBuf，负责释放它 |

为什么 Echo 示例不写 `release`？因为它继承了 `SimpleChannelInboundHandler<String>`，Netty 会在 `channelRead0` 返回后自动处理匹配消息的释放。不要把两种 Handler 的规则混在一起：

```text
SimpleChannelInboundHandler<T>：通常自动释放匹配的入站消息
ChannelInboundHandlerAdapter：通常由自己决定是否释放
```

### `readerIndex` 和 `writerIndex` 怎么理解

把 ByteBuf 想成一盒粉笔：

```text
已经读过的      还没读的数据           还可以继续写入
|-------------|---------------------|----------------|
0          readerIndex             writerIndex   capacity
```

- `readableBytes()`：还有多少数据可以读；
- `writableBytes()`：还剩多少空间可以写；
- `readInt()`：读取并移动 `readerIndex`；
- `getInt(0)`：读取但不移动 `readerIndex`；
- `writeInt(123)`：写入并移动 `writerIndex`。

协议解码器经常先用 `getInt` 偷看长度，确认完整后再用 `readInt` 真正消费。

------

## 0B.5 第四个案例：Pipeline 中的 `ctx` 到底怎么走

假设 Pipeline 是：

```text
Head -> A -> B -> C -> Tail
```

### 入站事件

网络收到数据后，入站事件从前往后：

```text
Head -> A -> B -> C -> Tail
```

如果 A 只是观察，不负责消费消息：

```java
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    System.out.println("A 看到了消息");
    ctx.fireChannelRead(msg);
}
```

`ctx.fireChannelRead(msg)` 的意思是：**从当前 Handler 的下一个入站位置继续走**。如果忘记调用，消息就停在 A，B 和 C 都收不到。

### 出站事件

业务 Handler 发出响应后，出站事件从后往前：

```text
Tail <- C <- B <- A <- Head
```

如果 B 是一个出站 Handler：

```java
@Override
public void write(
        ChannelHandlerContext ctx,
        Object msg,
        ChannelPromise promise) {
    System.out.println("B 处理出站消息");
    ctx.write(msg, promise);
}
```

`ctx.write(msg, promise)` 的意思是：**把消息交给当前 Handler 前面的出站 Handler**。如果忘记调用，消息会被 B 截断，无法到达 Socket。

### `ctx.write` 和 `channel.write` 的简单区别

```java
ctx.writeAndFlush(response);
```

从当前 Handler 所在位置向前走，通常适合当前业务 Handler 回写。

```java
ctx.channel().writeAndFlush(response);
```

从整条 Pipeline 的尾部开始找出站 Handler，通常会经过更多出站处理步骤。

初学时优先使用 `ctx.writeAndFlush`，但前提是确认编码器位于正确位置。

------

## 0B.6 第五个案例：两个客户端互相聊天

Echo 只有“发给谁，回给谁”。聊天室多了一件事：**把一条消息写给多个 Channel。**

Netty 提供 `ChannelGroup` 保存在线连接：

```java
private static final ChannelGroup ONLINE =
        new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
```

最小的聊天 Handler：

```java
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

    private static void broadcast(String text) {
        ONLINE.writeAndFlush(text);
    }
}
```

Pipeline 仍然是文本换行协议：

```java
pipeline.addLast(new LineBasedFrameDecoder(1024));
pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
pipeline.addLast(new ChatHandler());
```

这段代码体现了 Echo 到聊天室的变化：

| Echo | 聊天室 |
| --- | --- |
| 只处理当前 `ctx.channel()` | 需要保存所有在线 Channel |
| 写回当前连接 | 广播到 `ChannelGroup` |
| 没有连接级状态 | 至少需要处理加入、离开、广播 |

配套可运行代码：

`示例代码/src/main/java/note/io/netty/chat/NettyChatServer.java`

`示例代码/src/main/java/note/io/netty/chat/NettyChatClient.java`

运行方式见本目录的 `示例代码/README.md`。启动一个服务端，再启动两个客户端，在任意客户端输入一行文字，另一个客户端就能看到广播。

这只是学习版聊天室。生产系统还要继续考虑：

- 某个慢客户端不可写时是否跳过、断开或落盘；
- 用户名和身份放在哪里；
- 重复登录如何处理；
- 消息是否需要 ACK、幂等和离线存储；
- 广播数量很大时如何分组、限速和削峰。

------

## 0B.7 第六个案例：耗时业务为什么要换线程

假设收到消息后要查询数据库：

```java
protected void channelRead0(
        ChannelHandlerContext ctx, String message) {
    User user = userDao.find(message); // 假设耗时 3 秒
    ctx.writeAndFlush(user.name() + "\n");
}
```

如果这个 Handler 默认运行在 EventLoop 上，那么这 3 秒里，该 EventLoop 管理的其他连接也可能无法及时处理读写。

可以把“阻塞业务 Handler”放到专门的执行器：

```java
DefaultEventExecutorGroup businessGroup =
        new DefaultEventExecutorGroup(4);

pipeline.addLast(new LineBasedFrameDecoder(1024));
pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
pipeline.addLast(businessGroup, "business",
        new SimpleChannelInboundHandler<String>() {
            @Override
            protected void channelRead0(
                    ChannelHandlerContext ctx, String message) {
                User user = userDao.find(message);
                ctx.writeAndFlush(user.name() + "\n");
            }
        });
```

这里要特别注意：`StringEncoder` 放在业务 Handler 前面，是因为 `ctx.writeAndFlush` 会沿出站方向向前寻找编码器；入站时它会跳过，不影响前面的 String 解码。

注意三件事：

1. 只有这个业务 Handler 换到 `businessGroup`，不是整条 Pipeline 都换线程。
2. 线程池要有界，并处理队列满、超时、拒绝和服务降级。
3. 不要在 EventLoop 中写 `future.sync()`、`future.get()`、`join()` 或长时间 `sleep()`。

如果外部服务本身提供异步 API，更好的方式是组合异步结果，而不是用更多线程掩盖阻塞。

------

## 0B.8 第七个案例：从 String 升级为自定义 Message

文本协议适合入门，但真实服务通常需要：消息类型、请求编号、版本和长度。示例代码中的 `Message` 是：

```java
new Message(1, 42, "你好，Netty");
```

它在网络上会被编码成：

```text
固定头 16 字节 + UTF-8 正文

magic       4 字节：确认这是 LNIO 协议
version     1 字节：当前版本 1
type        1 字节：请求、响应、心跳等
flags       2 字节：预留字段
requestId   4 字节：把响应对应回请求
bodyLength  4 字节：正文占多少字节
body        N 字节：UTF-8 正文
```

Pipeline：

```java
pipeline.addLast("frameDecoder",
        new LengthFieldBasedFrameDecoder(
                1024 * 1024 + 16,
                12,
                4,
                0,
                0));
pipeline.addLast("messageDecoder", new MessageDecoder());
pipeline.addLast("messageEncoder", new MessageEncoder());
pipeline.addLast("requestHandler", new RequestHandler());
```

入站时：

```text
网络字节
 -> LengthFieldBasedFrameDecoder：等够一整帧
 -> MessageDecoder：ByteBuf -> Message
 -> RequestHandler：处理 Message
```

出站时：

```text
RequestHandler：writeAndFlush(Message)
 -> MessageEncoder：Message -> ByteBuf
 -> 网络字节
```

`requestId` 的意义可以用并发请求理解：

```text
客户端发送 requestId=41：查询 A
客户端发送 requestId=42：查询 B

服务端先返回 requestId=42
服务端再返回 requestId=41
```

客户端不能依赖返回顺序，只能通过 `requestId` 找到对应请求。

完整代码：

`示例代码/src/main/java/note/io/netty/protocol/ProtocolServer.java`

`示例代码/src/main/java/note/io/netty/protocol/ProtocolClient.java`

先把它当成“更严格的 Echo”：服务端仍然返回原文，只是把文本包进了 `Message`。

------

## 0B.9 第八个案例：心跳不是“连接还在”这么简单

TCP 连接显示 active，只能说明本地暂时认为连接存在。对端进程卡死、网络路径断开、NAT 丢状态时，本地不一定马上知道。

Netty 可以用 `IdleStateHandler` 产生空闲事件：

```java
pipeline.addLast(new IdleStateHandler(
        60, // 60 秒没有读到数据
        20, // 20 秒没有写出数据
        0,
        TimeUnit.SECONDS));
pipeline.addLast(new HeartbeatHandler());
```

业务 Handler 处理事件：

```java
@Override
public void userEventTriggered(
        ChannelHandlerContext ctx, Object event) {
    if (event instanceof IdleStateEvent idle
            && idle.state() == IdleState.WRITER_IDLE) {
        ctx.writeAndFlush(new Message(9, 0, "")); // Ping
        return;
    }
    ctx.fireUserEventTriggered(event);
}
```

简单理解：

```text
长时间没有写出数据 -> 发 Ping
长时间没有读到有效数据 -> 关闭或进入重连
收到 Ping -> 回复 Pong
```

心跳只能检测和维持链路，不能代替业务 ACK。`writeAndFlush` 成功也不代表对端业务已经处理。

------

## 0B.10 看代码时固定问这六个问题

以后打开任何 Netty 项目，按这个顺序读：

### 1. 这条 Channel 是谁？

是监听端口的 `ServerChannel`，还是某个客户端连接的 `SocketChannel`？

### 2. 这个 Handler 收到的 `msg` 类型是什么？

看它前面的 Decoder，不要只看变量名。常见类型有：

```text
ByteBuf -> String -> 自定义 Message -> 业务响应
```

### 3. 这个 Handler 消费消息还是转交消息？

- 消费：完成业务，并按规则释放引用计数对象；
- 转交：调用 `ctx.fireChannelRead(msg)`，不要提前 release。

### 4. 这次写操作从哪里开始走？

- `ctx.write(...)`：从当前 Context 向前找出站 Handler；
- `channel.write(...)`：从整条 Pipeline 的尾部开始；
- `write`：进入待写队列；
- `flush`：请求把待写数据推进到底层。

### 5. 这段代码在哪个线程执行？

默认是 Channel 所属 EventLoop；如果 `pipeline.addLast(executor, handler)`，就要看指定的执行器。发现数据库、文件、HTTP 调用时，先检查是否阻塞 EventLoop。

### 6. 消息边界和最大长度在哪里保证？

找 `LineBasedFrameDecoder`、`LengthFieldBasedFrameDecoder` 或自定义 `ByteToMessageDecoder`。找不到分帧和最大长度限制，就不要急着相信业务 Handler 收到的是“完整消息”。

------

## 0B.11 推荐的动手顺序

每次只改一个地方，并先猜结果再运行：

1. 运行 Netty Echo，观察客户端发送 `hello netty\n` 后服务端的 Handler 入参。
2. 删除 `StringDecoder`，看为什么 `SimpleChannelInboundHandler<String>` 不再适配 `ByteBuf`。
3. 删除 `LineBasedFrameDecoder`，连续发送两行，观察业务边界不再可靠。
4. 把 `channelRead0` 中的 `ctx.writeAndFlush` 改成只 `ctx.write`，观察为什么可能迟迟收不到响应。
5. 在 Handler 中打印 `Thread.currentThread().getName()`，再开两个客户端。
6. 运行协议 Echo，修改 `requestId`，观察响应如何保留请求编号。
7. 运行聊天室，开启两个客户端，体验 `ChannelGroup` 广播。
8. 在业务 Handler 中加入 3 秒延迟，再移动到 `DefaultEventExecutorGroup`，比较两个连接的响应。
9. 用 `EmbeddedChannel` 把一帧拆成三段输入，观察分帧器如何等待数据。

推荐运行顺序和端口见：[[示例代码/README]]。

------

## 0B.12 读懂这一章的最低标准

如果下面的问题你能用自己的话回答，就可以继续读第 7～13 章：

**问：`channelRead0` 为什么能拿到 String？**

答：因为前面的分帧器和 `StringDecoder` 已经把网络字节处理成了 String。

**问：为什么 Echo 必须发送换行符？**

答：当前协议用换行表示一条消息结束；没有换行，`LineBasedFrameDecoder` 会继续等待。

**问：`ctx.fireChannelRead(msg)` 做什么？**

答：把入站消息交给当前 Handler 后面的 Handler；不调用就会截断事件传播。

**问：`writeAndFlush` 做什么？**

答：发起出站写操作并 flush；消息会按出站方向经过编码器，最后写到网络。

**问：为什么不能在 EventLoop 中查询数据库？**

答：一个 EventLoop 通常负责多个 Channel，阻塞一个连接可能拖慢同一线程上的其他连接。

**问：聊天室为什么需要 ChannelGroup？**

答：Echo 只需要当前 Channel；聊天室需要保存并向多个在线 Channel 广播。

------

上一章：[[00A-Netty新手术语与读代码指南]]　下一章：[[06-Netty入门与第一个Echo服务]]
