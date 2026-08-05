# 七、EventLoop 与线程模型

> 先复习：`EventLoop` 可以理解为“固定负责一批连接的循环线程”。它不是每次请求都会新建的线程，也不是业务线程池的同义词。

> 白话翻译：一个接线员可以同时照看多条电话线，但一次只能处理当前手里的事情。如果他去查数据库、睡 3 秒，其他电话也会跟着等；耗时工作要交给专门的业务人员。

## 7.1 最重要的线程所有权

一个 Channel 注册后，通常在生命周期内绑定到同一个 EventLoop；一个 EventLoop 可以管理多个 Channel。

```text
EventLoop-1 -> Channel A, B, C
EventLoop-2 -> Channel D, E
EventLoop-3 -> Channel F, G, H
```

因此同一 Channel 的普通入站事件按顺序执行，Handler 常可避免为“单连接状态”加锁。但不同 Channel 之间没有全局顺序，共享可变状态仍需线程安全。

## 7.2 EventLoop 不只做 IO

EventLoop 循环处理三类工作：

```text
select/处理就绪 IO
-> 执行普通任务队列
-> 执行到期的定时任务
-> 下一轮
```

`ctx.executor().execute(...)` 和 `schedule(...)` 最终会进入相关执行器。一个长任务会同时延迟该 EventLoop 上其他 Channel 的读写、心跳和定时器。

### 用一个时间线理解“为什么不能阻塞”

假设 Channel A 与 B 恰好由同一个 `nioEventLoop-3` 管理：

```text
10:00:00  A 收到请求 -> Handler 开始执行
10:00:00  Handler 中执行 JDBC 查询，阻塞 3 秒
10:00:01  B 的字节已经到达网卡，但 EventLoop 无法处理
10:00:03  A 的 JDBC 返回，EventLoop 才继续处理 B
```

所以“一个连接卡住”经常表现成“偶尔有很多连接一起慢”。正确做法不是给 IO 线程组盲目加线程，而是把确实会阻塞的业务移出 EventLoop，或改为异步客户端。

在 Netty 4.2 中，IO 线程组与具体的 NIO IO 实现被拆开了：使用 `MultiThreadIoEventLoopGroup` 管理线程，使用 `NioIoHandler.newFactory()` 提供 Selector 实现。旧的 `NioEventLoopGroup` 仍可见，但已标记为 deprecated。

## 7.3 教程代码：证明事件线程固定

```java
public final class ThreadProbeHandler
        extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        print("active", ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        print("read", ctx);
        ctx.fireChannelRead(msg);
    }

    private void print(String event, ChannelHandlerContext ctx) {
        System.out.printf("%s channel=%s thread=%s%n",
                event,
                ctx.channel().id().asShortText(),
                Thread.currentThread().getName());
    }
}
```

多开几个客户端，观察“同一 Channel 线程不变、多个 Channel 可能共享 EventLoop”。

## 7.4 阻塞业务如何切走

```java
DefaultEventExecutorGroup businessGroup =
        new DefaultEventExecutorGroup(16);

pipeline.addLast("frame", new LineBasedFrameDecoder(1024));
pipeline.addLast("decode", new StringDecoder(StandardCharsets.UTF_8));
pipeline.addLast("encode", new StringEncoder(StandardCharsets.UTF_8));
pipeline.addLast(businessGroup, "blockingBusiness",
        new BlockingBusinessHandler());
```

`encode` 要放在业务 Handler 前面：业务 Handler 使用 `ctx.writeAndFlush` 时，出站事件会向前传播，才能遇到 `StringEncoder`。

| 名称 | 含义 | 不要误解为 |
| --- | --- | --- |
| `businessGroup` | 专门运行耗时/阻塞 Handler 的执行器组 | 每条连接一个独立线程 |
| `pipeline.addLast(businessGroup, ...)` | 仅将这个 Handler 的回调调度到业务线程 | 整条 Pipeline 都离开 EventLoop |
| `ctx.executor()` | 当前 Handler 实际所属的执行器 | 永远等于 NIO EventLoop；若 Handler 被切换，它会是业务执行器 |
| `scheduleAtFixedRate` | 定期把一个任务提交到该执行器 | 精准实时定时器；繁忙时仍可能延后 |

传入执行器组后，该 Handler 的回调在业务线程执行，并对同一个 `ChannelHandlerContext` 保持有序。注意：

- 线程切换有排队与上下文成本，不应给每个轻量 Handler 都切线程。
- 业务线程池和队列必须有界，并定义超时、拒绝和降级策略。
- 回写可从业务线程调用，Netty 会把底层操作安全地提交回对应 EventLoop。
- 数据库驱动若已有异步 API，可通过 Future 组合，避免额外阻塞线程。

## 7.5 定时任务与取消

```java
ScheduledFuture<?> heartbeat = ctx.executor().scheduleAtFixedRate(
        () -> ctx.writeAndFlush(Ping.INSTANCE),
        5, 5, TimeUnit.SECONDS);

ctx.channel().closeFuture().addListener(future ->
        heartbeat.cancel(false));
```

任务与连接生命周期绑定。忘记取消持有 Channel 的周期任务，会造成无效执行和对象无法回收。

## 7.6 Handler 是否能共享

标有 `@ChannelHandler.Sharable` 的 Handler 可加入多个 Pipeline，但前提是实例字段没有非线程安全的“每连接状态”。连接级状态应放在：

- `Channel` 的 `AttributeKey`；
- 专属于该 Channel 的 Handler 实例；
- 消息对象或外部并发安全存储。

不能仅仅为了少创建对象就给有可变字段的 Handler 加 `@Sharable`。

## 7.7 官方 API

- [EventLoop](https://netty.io/4.2/api/io/netty/channel/EventLoop.html)
- [MultiThreadIoEventLoopGroup](https://netty.io/4.2/api/io/netty/channel/MultiThreadIoEventLoopGroup.html)
- [NioIoHandler](https://netty.io/4.2/api/io/netty/channel/nio/NioIoHandler.html)
- [DefaultEventExecutorGroup](https://netty.io/4.2/api/io/netty/util/concurrent/DefaultEventExecutorGroup.html)
- [ChannelHandler.Sharable](https://netty.io/4.2/api/io/netty/channel/ChannelHandler.Sharable.html)
- [AttributeKey](https://netty.io/4.2/api/io/netty/util/AttributeKey.html)

## 7.8 知识问答

**问：EventLoop 线程数是不是越多越好？**

答：不是。IO 线程过多会增加调度、缓存失效和 Selector 成本。先用默认值与压测数据，再根据连接数、事件处理时长和 CPU 核数调整。

**问：同一 Channel 的 Handler 都在同一线程吗？**

答：默认是绑定 EventLoop 的线程；显式给 Handler 指定 `EventExecutorGroup` 后会发生线程切换。

**问：能否在 Handler 中使用 `Thread.sleep` 模拟耗时？**

答：只适合故障实验。它会阻塞执行该 Handler 的线程；在 EventLoop 上会拖慢多条连接。

**问：从业务线程调用 `writeAndFlush` 安全吗？**

答：Netty 的 Channel API 支持跨线程调用，并把工作调度到正确 EventLoop；但业务对象自身仍要满足并发和生命周期约束。

### 动手题

在读 Handler 中休眠 3 秒，用两个客户端同时发送；再把 Handler 放到 `DefaultEventExecutorGroup`，比较第二个客户端的延迟。

------

上一章：[[06-Netty入门与第一个Echo服务]]　下一章：[[08-ByteBuf与引用计数]]
