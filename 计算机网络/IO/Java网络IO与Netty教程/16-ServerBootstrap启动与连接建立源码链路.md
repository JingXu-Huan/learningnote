# 十六、ServerBootstrap 启动与连接建立源码链路

> 白话翻译：启动代码只有十几行，但背后要完成“创建服务端 Channel、注册 boss EventLoop、绑定端口、接收客户端、把子 Channel 注册给 worker”五件事。本章只追主干，不钻进每一个平台细节。

## 16.1 源码阅读前先固定问题

读源码不是从第一行一路翻到底。先带着五个问题：

1. `NioServerSocketChannel` 在哪里创建？
2. 服务端 Channel 为什么注册到 boss？
3. `bind()` 真正调用 JDK 哪个对象绑定端口？
4. accept 得到的客户端 Channel 在哪里创建？
5. `childHandler` 何时加入子 Channel，子 Channel 又如何交给 worker？

示例入口：

```java
ServerBootstrap bootstrap = new ServerBootstrap()
        .group(boss, worker)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel) {
                channel.pipeline().addLast(new BusinessHandler());
            }
        });

Channel server = bootstrap.bind(9000).sync().channel();
```

## 16.2 先分清两种 Channel

| 对象 | 作用 | EventLoopGroup | 配置入口 |
| --- | --- | --- | --- |
| `NioServerSocketChannel` | 监听端口、接收连接 | boss | `option`、`handler` |
| `NioSocketChannel` | 与一个客户端收发数据 | worker | `childOption`、`childHandler` |

`ServerBootstrap` 的“server”和“child”语义贯穿整条源码链。如果这里混淆，后面很容易误以为 boss 会读取业务数据。

## 16.3 第一条主线：bind 如何完成端口监听

下面省略了失败处理和平台分支，只保留职责：

```text
ServerBootstrap.bind(port)
  -> AbstractBootstrap.bind(...)
     -> initAndRegister()
        -> channelFactory.newChannel()
           创建 NioServerSocketChannel
        -> ServerBootstrap.init(channel)
           安装服务端配置与 ServerBootstrapAcceptor
        -> bossGroup.register(channel)
           选择一个 boss EventLoop，注册 Channel
     -> doBind0(...)
        -> channel.bind(localAddress)
           -> Pipeline 出站 bind 事件
              -> HeadContext
                 -> Channel.Unsafe.bind(...)
                    -> NioServerSocketChannel.doBind(...)
                       -> JDK ServerSocketChannel.bind(...)
```

需要抓住三个时机：

- **newChannel**：只创建对象，端口还没有监听；
- **register**：Channel 获得固定 EventLoop，Pipeline 触发注册相关事件；
- **bind**：真正绑定本地地址，成功后服务端 Channel 进入 active 状态。

`bind()` 返回 `ChannelFuture`，因为注册和绑定可能被调度到 EventLoop 异步完成。调用 `sync()` 只是示例主线程等待结果，不会把 Netty 的 IO 模型变成阻塞 IO。

## 16.4 `channel(Class)` 背后的工厂

```java
.channel(NioServerSocketChannel.class)
```

这不是立即创建 Channel，而是为 Bootstrap 设置 ChannelFactory。真正执行 `newChannel()` 是 `initAndRegister()`。因此 Bootstrap 是“创建与配置 Channel 的模板”，Channel 才是实际运行的连接对象。

如果 `channel()`、`group()` 或 `childHandler()` 缺失，通常在启动校验或初始化阶段失败，而不是等第一条业务消息到来。

## 16.5 `ChannelInitializer` 为什么用完会消失

`ChannelInitializer` 是一个临时安装器。Channel 注册时，它的 `handlerAdded/channelRegistered` 路径会调用 `initChannel`，把真正的业务 Handler 加入 Pipeline；初始化完成后，它会把自己移除。

```text
初始 Pipeline
  -> ChannelInitializer

注册并初始化后
  -> Decoder -> Encoder -> BusinessHandler
```

每个子 Channel 都有独立 Pipeline，所以 `initChannel` 会为每个新连接执行一次。若加入的是同一个有可变字段的 Handler 实例，必须确认它标记为 `@Sharable` 且确实线程安全。

## 16.6 第二条主线：一个客户端如何进入 worker

服务端 Channel 收到 accept 就绪事件后，主干可以理解为：

```text
boss EventLoop 检测到 accept 就绪
  -> 服务端 Channel 的 read 路径
     -> doReadMessages(...)
        -> JDK ServerSocketChannel.accept()
           -> 创建代表客户端的 NioSocketChannel
     -> serverChannel.pipeline().fireChannelRead(child)
        -> ServerBootstrapAcceptor.channelRead(...)
           -> child.pipeline().addLast(childHandler)
           -> 设置 childOption / childAttr
           -> workerGroup.register(child)
              -> 选择一个 worker EventLoop
              -> 执行 ChannelInitializer
              -> child 注册并激活
              -> 开始读取客户端业务数据
```

`ServerBootstrapAcceptor` 是理解 boss/worker 交接的关键：它在服务端 Channel 的 Pipeline 中接收“刚 accept 出来的子 Channel”，安装子配置，再把它注册给 childGroup，也就是 worker。

## 16.7 为什么一个 Channel 固定一个 EventLoop

注册完成后，Channel 的生命周期事件、IO 事件和普通任务都由固定 EventLoop 串行执行：

```text
worker EventLoop A -> Channel 1、Channel 4、Channel 7
worker EventLoop B -> Channel 2、Channel 5
worker EventLoop C -> Channel 3、Channel 6
```

这让单连接内部的大部分状态无需加锁，同时保证事件顺序。它并不表示一个 EventLoop 只服务一个 Channel；耗时 Handler 仍会拖慢同一 EventLoop 上的其他连接。

从外部线程调用 `channel.writeAndFlush()` 时，Netty 会把任务提交给该 Channel 的 EventLoop。顺序保证依赖正确的执行器边界，不等于所有跨 Channel 共享状态都自动线程安全。

## 16.8 option、childOption 为什么在不同阶段生效

```text
option       -> 服务端监听 Channel -> bind 前后使用
childOption  -> accept 得到的子 Channel -> 注册 worker 前设置
```

例如：

- `SO_BACKLOG` 影响监听连接队列，属于 `option`；
- `TCP_NODELAY` 影响每条已连接 TCP Socket，属于 `childOption`；
- `SO_KEEPALIVE` 通常也是子 Channel 选项。

把选项写错区域可能不会得到想要的效果，并可能在日志中看到不支持该 Channel 类型的警告。

## 16.9 用调试器验证主链

建议按下面顺序打断点，不要一次打几十个：

1. `AbstractBootstrap.initAndRegister`：观察服务端 Channel 创建；
2. `ServerBootstrap.init`：观察服务端 Pipeline 和 Acceptor 安装；
3. `AbstractChannel.AbstractUnsafe.register`：观察 EventLoop 绑定；
4. `NioServerSocketChannel.doBind`：观察本地地址绑定；
5. 服务端 Channel 的 `doReadMessages`：观察 JDK accept；
6. `ServerBootstrapAcceptor.channelRead`：观察 childHandler 与 worker 注册；
7. 自己的 `ChannelInitializer.initChannel`：确认每条子连接执行一次。

调试时同时观察：当前线程名、`channel.getClass()`、`channel.eventLoop()`、Pipeline 中 Handler 名称和 Channel 的 local/remote address。

> 版本提示：Netty 4.2 推荐 `MultiThreadIoEventLoopGroup + NioIoHandler`，底层就绪事件驱动的类名与 4.1 教程可能不同；先追上述稳定职责，再对照当前版本源码，不要死背某一版的所有私有方法。

## 16.10 常见理解误区

**误区一：boss 负责所有读写，只把业务任务交给 worker。**

boss 主要负责监听 Channel 的 accept；子 Channel 注册到 worker 后，网络读写与其 Pipeline 事件由 worker EventLoop 驱动。

**误区二：调用 `bind().sync()` 后所有处理都在主线程。**

主线程只是等待绑定完成；accept 和连接 IO 仍由 EventLoop 执行。

**误区三：`childHandler` 安装在服务端 Channel。**

它安装在每个 accept 出来的子 Channel；服务端 Channel 内部使用 Acceptor 完成交接。

**误区四：一个 worker 线程对应一个客户端。**

一个 EventLoop 可管理很多 Channel，Channel 只固定归属其中一个 EventLoop。

## 16.11 官方源码入口

- [ServerBootstrap API](https://netty.io/4.2/api/io/netty/bootstrap/ServerBootstrap.html)
- [AbstractBootstrap 源码](https://netty.io/4.2/xref/io/netty/bootstrap/AbstractBootstrap.html)
- [ServerBootstrap 源码](https://netty.io/4.2/xref/io/netty/bootstrap/ServerBootstrap.html)
- [NioServerSocketChannel 源码](https://netty.io/4.2/xref/io/netty/channel/socket/nio/NioServerSocketChannel.html)
- [AbstractChannel 源码](https://netty.io/4.2/xref/io/netty/channel/AbstractChannel.html)
- [ChannelInitializer API](https://netty.io/4.2/api/io/netty/channel/ChannelInitializer.html)

## 16.12 知识问答

**问：端口绑定前为什么先注册 EventLoop？**

答：后续 Channel 状态变更和事件需要在明确的线程所有权下执行。注册先建立 Channel 与 EventLoop 的关系，再由该执行器完成绑定及后续事件传播。

**问：新连接是在哪里从 boss 交给 worker 的？**

答：accept 创建子 Channel 后，服务端 Pipeline 中的 `ServerBootstrapAcceptor` 配置子 Channel，并调用 childGroup 注册；childGroup 就是传入 `group(boss, worker)` 的 worker。

**问：源码学习需要记住全部方法名吗？**

答：不需要。先记住创建、初始化、注册、绑定、accept、子 Channel 注册六个职责和它们的线程边界；版本升级时再用断点对照具体类名。

### 动手题

1. 在 Echo 服务端的 `initChannel`、`channelRegistered`、`channelActive` 打印线程名与 Channel 类型，连接两个客户端观察顺序。
2. 分别给服务端 Channel 和子 Channel 打印 Pipeline，找出 Acceptor 与自己的业务 Handler。
3. 画出一个 boss、两个 worker、五个客户端的 Channel 归属图，并解释某个业务 Handler 阻塞时影响哪些连接。

------

上一章：[[15-WebSocket长连接实战]]　返回：[[../Java网络IO与Netty学习指南|学习指南]]
