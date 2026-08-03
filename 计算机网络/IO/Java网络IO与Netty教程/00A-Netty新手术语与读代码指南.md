# Netty 新手术语与读代码指南

> 适用对象：会写 Java、但第一次接触 Netty，看到 `ctx`、`msg`、`Channel`、`EventLoop` 等名字会发懵的同学。
>
> 读完本章再进入第六章。目标不是背 API，而是能把每一行示例代码放回“谁在做什么、数据往哪里走”的图里。

------

## 0.1 先建立一个生活化模型

把 Netty 服务端想成一家餐厅：

| Netty 名词 | 餐厅类比 | 在程序里真正做的事 |
| --- | --- | --- |
| `ServerBootstrap` | 开店筹备清单 | 配置服务器、线程组、端口和新连接的处理流程 |
| `Channel` | 一扇门/一条专属通道 | 代表监听端口或一条 TCP 连接，可读、可写、可关闭 |
| `EventLoop` | 固定服务员 | 轮询 IO 事件并执行该连接上的 Handler 代码 |
| `ChannelPipeline` | 菜品处理流水线 | 保存多个 Handler，决定消息依次经过谁 |
| `Handler` | 流水线的一个工位 | 只负责一个小步骤：拆包、解码、鉴权、业务、编码等 |
| `ByteBuf` | 装原料的可重复使用周转箱 | 保存网络字节，带读写位置和引用计数 |
| `ChannelHandlerContext`（`ctx`） | 当前工位的工作台 | 能访问当前 Channel、Pipeline、执行器，并把事件传给相邻工位 |
| `Future` / `ChannelFuture` | 取餐凭条 | 操作已发起但尚未完成；可等待或注册完成回调 |

关键点：**Netty 没有“收到一条 Java 对象”的魔法。网络先到的是字节，字节先进入 `ByteBuf`，经过解码器后才可能成为 `String` 或 `Message`。**

## 0.2 一次 Echo 请求到底发生了什么

客户端发送 `hello\n` 时，服务端的典型路径如下：

```text
客户端 TCP 字节: h e l l o \n
        |
        v
NioSocketChannel 收到字节，所属 EventLoop 被唤醒
        |
        v
ByteBuf("hello\n")
        |
        v
LineBasedFrameDecoder     按 \n 切出一帧
        |
        v
StringDecoder             UTF-8 字节 -> Java String "hello"
        |
        v
EchoHandler.channelRead0  业务逻辑：拼接 "echo: hello\n"
        |
        v
StringEncoder             Java String -> UTF-8 字节
        |
        v
Socket 写出给客户端
```

这里最容易误会两件事：

1. TCP 不认识换行符，也不认识“hello 是一条消息”；`LineBasedFrameDecoder` 是**应用层协议规则**。
2. `EchoHandler` 的入参是 `String`，不是因为 Socket 直接读到了 String，而是前面的 Handler 已经转换过类型。

## 0.3 必须分清的四组概念

### 阻塞与非阻塞

- 阻塞：调用线程在结果出现前停住，例如传统 `InputStream.read()`。
- 非阻塞：调用立刻返回；暂时没数据时不会让线程睡在一次 read 上。

### 同步与异步

- 同步：调用者主动问结果或等待结果，例如 `future.sync()`。
- 异步：先交出任务，结果到来时通过回调/监听器通知，例如 `future.addListener(...)`。

它们是两条坐标轴。Netty 的底层 IO 通常是非阻塞的；教程在 `main` 中调用 `sync()`，只是为了防止 main 线程提前退出，并不表示网络模型退化为阻塞 IO。

### 服务端 Channel 与子 Channel

```text
NioServerSocketChannel       监听 9003，只负责接收连接
          |
          | accept 一个客户端
          v
NioSocketChannel             表示“服务端 <-> 某一客户端”的一条 TCP 连接
```

因此 `option(...)` 用于监听 Channel，`childOption(...)` 与 `childHandler(...)` 用于后来创建的每个 `NioSocketChannel`。

### 入站与出站

- 入站（inbound）：外部世界进入程序，例如连接建立、收到字节、连接关闭、异常。
- 出站（outbound）：程序主动向外部世界发起，例如 `write`、`flush`、`connect`、`close`。

不要把“请求”和“响应”等同于入站和出站：服务端收到请求是入站；服务端发送响应是出站。客户端则相反。

## 0.4 示例代码变量名逐个翻译

以下代码来自 `NettyEchoServer`，变量名都是行业常见约定：

```java
NioEventLoopGroup boss = new NioEventLoopGroup(1);
NioEventLoopGroup worker = new NioEventLoopGroup();
ServerBootstrap bootstrap = new ServerBootstrap();
Channel server = bootstrap.bind(PORT).sync().channel();
```

| 名称 | 全称/直觉 | 生命周期 | 新手应记住什么 |
| --- | --- | --- | --- |
| `boss` | boss group，接待新连接的一组线程 | 服务器启动到关闭 | 通常只做 `accept`，不是业务线程 |
| `worker` | worker group，处理已连接客户端的一组线程 | 服务器启动到关闭 | 读写事件和默认 Handler 回调在这里运行 |
| `bootstrap` | 启动配置器 | 配置完成后用于 bind | 它不是服务器本身，而是“造服务器的说明书” |
| `server` | server channel | bind 成功到服务关闭 | 是监听端口的 Channel，不是一名客户端 |
| `channel` | 当前正在初始化的子连接 | 单个客户端连接的生命周期 | 在 `initChannel` 中就是某个新客户端对应的 `SocketChannel` |
| `ctx` | context，上下文 | 单个 Handler 加入 Pipeline 后存在 | 当前 Handler 的位置与操作入口 |
| `msg` / `message` | message，当前消息 | 一次事件回调 | 类型取决于前面 Handler 的输出，可能是 `ByteBuf`、`String` 或业务对象 |
| `out` | output，编码器输出缓冲区 | 一次 `encode` 回调 | 编码器把字节写进它，不要手动释放它 |
| `in` | input，解码器输入缓冲区 | 一次 `decode` 回调 | 读取前先判断可读字节数；数据不全就 return |
| `future` | 未来结果 | 一次异步操作 | 用监听器处理完成/失败，避免在 EventLoop 中 `sync()` |
| `promise` | 可被完成的未来结果 | 一次出站操作 | 多见于自定义出站 Handler，向下游报告成功或失败 |

## 0.5 `ctx`、`channel`、`pipeline` 到底怎么选

它们三者有关联，但职责不同：

```java
void channelRead(ChannelHandlerContext ctx, Object msg) {
    Channel currentChannel = ctx.channel();
    ChannelPipeline wholePipeline = ctx.pipeline();
}
```

| 需要做的事 | 常用入口 | 理由 |
| --- | --- | --- |
| 获取远端地址、ID、是否可写 | `ctx.channel()` | 这是当前连接本身 |
| 继续把入站消息交给后面 Handler | `ctx.fireChannelRead(msg)` | 从当前 Handler 的下一个入站节点继续 |
| 把当前响应交给前面的出站编码器 | `ctx.writeAndFlush(response)` | 从当前位置反向传播，常用于业务 Handler 回写 |
| 查看/增删处理链 | `ctx.pipeline()` | 得到整条 Pipeline |
| 给整个链尾部发起一次写操作 | `ctx.channel().writeAndFlush(msg)` | 从完整出站链路开始；要确认不会重复经过不该经过的 Handler |

初学阶段可以遵循一句规则：**在 Handler 内处理当前事件用 `ctx`；需要描述连接状态时用 `ctx.channel()`。**

## 0.6 第一份 Pipeline 应该这样读

```java
channel.pipeline()
        .addLast(new LineBasedFrameDecoder(1024))
        .addLast(new StringDecoder(StandardCharsets.UTF_8))
        .addLast(new StringEncoder(StandardCharsets.UTF_8))
        .addLast(new EchoHandler());
```

不要把这四行当成“固定模板”，请按类型和方向阅读：

| 顺序 | Handler | 入站输入 -> 输出 | 出站时是否参与 | 责任 |
| --- | --- | --- | --- | --- |
| 1 | `LineBasedFrameDecoder` | `ByteBuf` -> 一帧 `ByteBuf` | 否 | 找到 `\n`，解决拆包/粘包 |
| 2 | `StringDecoder` | 一帧 `ByteBuf` -> `String` | 否 | 按 UTF-8 翻译字节 |
| 3 | `StringEncoder` | 无 | `String` -> `ByteBuf` | 把响应编码回字节 |
| 4 | `EchoHandler` | `String` -> 业务处理 | 发起写操作 | 拼出 Echo 响应 |

`StringEncoder` 虽然添加在 `EchoHandler` 前面，但出站时方向相反，所以 Echo 写出的 String 会先遇到它。这是 Pipeline 里最值得亲手画一遍的方向问题。

## 0.7 看不懂回调方法时，先问三个问题

| 方法 | 什么时候调用 | `msg` 是什么 | 你通常要做什么 |
| --- | --- | --- | --- |
| `channelActive(ctx)` | TCP 连接成功建立 | 没有消息 | 初始化连接状态、记录日志、可发送欢迎包 |
| `channelRead(ctx, msg)` | 收到一段已经被上游处理过的数据 | 取决于上游 | 处理或 `fireChannelRead(msg)`；注意 ByteBuf 所有权 |
| `channelRead0(ctx, message)` | `SimpleChannelInboundHandler<T>` 收到匹配 T 的消息 | 泛型 `T` | 写业务逻辑；默认会自动释放匹配的引用计数消息 |
| `channelReadComplete(ctx)` | 本轮读取结束 | 没有消息 | 通常交给框架，少数场景统一 flush |
| `exceptionCaught(ctx, cause)` | 链路出现异常 | `cause` 是异常原因 | 记录上下文，决定是否关闭连接或返回错误码 |
| `write(ctx, msg, promise)` | 程序要写数据 | 待发送对象 | 编码/拦截后调用 `ctx.write(msg, promise)` |

## 0.8 初学者最常踩的坑

1. **以为 `channelRead0` 中拿到的一定是字节。** 实际类型由 Pipeline 决定；先看前面的解码器。
2. **在 `channelRead` 里既 `release(msg)` 又 `fireChannelRead(msg)`。** 下游会拿到已释放对象；二选一：消费并释放，或交给下游。
3. **在 EventLoop 中查数据库、`Thread.sleep`、`future.sync()`。** 一个线程会拖慢它负责的多条连接。
4. **没有分帧器就直接 `StringDecoder`。** TCP 可能半条、多条一起到达，业务边界不可靠。
5. **把每个 Handler 都做成 `@Sharable`。** 有连接级变量时会让不同连接并发污染状态。
6. **只调用 `write`，不调用 `flush`。** 数据可能还留在缓冲区；日常响应使用 `writeAndFlush` 更直观。

## 0.9 建议的学习动作

1. 先运行 Netty Echo；客户端只发送 `hello\n`。
2. 在 `EchoHandler.channelRead0` 打印 `message.getClass()`，确认它是 `String`。
3. 删除 `StringDecoder` 再运行，观察 Handler 类型不匹配的现象；随后恢复。
4. 打开配套的 [Netty 机制交互动画](动画/netty-mechanism.html)，分别点 EventLoop、Pipeline、ByteBuf。
5. 再读第六章，并在纸上画出一次请求的入站箭头和一次响应的出站箭头。

## 0.10 官方 API 起读点

- [Channel](https://netty.io/4.1/api/io/netty/channel/Channel.html)
- [ChannelHandlerContext](https://netty.io/4.1/api/io/netty/channel/ChannelHandlerContext.html)
- [ChannelPipeline](https://netty.io/4.1/api/io/netty/channel/ChannelPipeline.html)
- [EventLoop](https://netty.io/4.1/api/io/netty/channel/EventLoop.html)
- [ByteBuf](https://netty.io/4.1/api/io/netty/buffer/ByteBuf.html)

### 自测问答

**问：`ctx` 是整条连接吗？**

答：不是。它是“某个 Handler 在某条 Pipeline 中的位置”。通过它可以取得连接 `Channel`，但同一条连接上的不同 Handler 各有自己的 ctx。

**问：为什么 `msg` 不直接叫 `String`？**

答：Netty 是通用网络框架，消息可能是字节、HTTP 对象、WebSocket 帧或自定义对象。`msg` 是通用命名；当 Handler 明确只处理 String 时，示例会用 `message` 让含义更清楚。

**问：只学 Netty 能跳过 NIO 吗？**

答：可以先用 Netty 写程序，但至少要理解“非阻塞、事件循环、字节流没有边界”三个 NIO 背景；否则遇到线程阻塞、半包和引用计数问题很难定位。

------

上一章：[[00-学习路线与环境准备]]　下一章：[[06-Netty入门与第一个Echo服务]]
