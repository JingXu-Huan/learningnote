# 九、Pipeline 与 Handler 事件传播

> 把 Pipeline 画成一排工位。`ctx` 指向“我所在的工位”，而 `channel` 代表“这条连接本身”。

## 9.1 Pipeline 是双向链

每条 Channel 都有自己的 `ChannelPipeline`。入站事件从 Head 向 Tail，出站事件从 Tail 向 Head：

```text
入站事件：
Head -> frameDecoder -> messageDecoder -> business -> Tail

出站事件：
Head <- frameEncoder <- messageEncoder <- business <- Tail
```

典型入站：注册、激活、读取、用户事件、异常；典型出站：bind、connect、write、flush、close。

## 9.2 ctx 与 channel 发起出站的区别

假设顺序为 `A -> B -> C`，代码当前在 B：

```java
ctx.writeAndFlush(msg);
```

从 B 的前一个节点向 Head 传播，跳过 B 之后的 C。

```java
ctx.channel().writeAndFlush(msg);
```

从 Pipeline Tail 开始完整向前传播，可经过 C、B、A 的出站处理。选择哪一个取决于期望经过哪些编码器，而不是个人习惯。

### 为什么方向会让人困惑

如果 A、B、C 都是 `ChannelDuplexHandler`，当前正位于 B：

```text
入站字节：  Head -> A -> B（当前） -> C -> Tail
ctx.fireChannelRead(msg)：                B -> C -> Tail

业务回写：  Tail <- C <- B（当前） <- A <- Head
ctx.write(msg)：                          B -> A -> Head
channel.write(msg)：             Tail -> C -> B -> A -> Head
```

实际 Pipeline 常混有“只入站”或“只出站” Handler，框架会自动跳过不关心该事件的节点。初学时先用全双工图建立方向感。

## 9.3 教程代码：打印事件方向

```java
public final class FlowLogHandler extends ChannelDuplexHandler {
    private final String name;

    public FlowLogHandler(String name) {
        this.name = name;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println(name + " inbound");
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(
            ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        System.out.println(name + " outbound");
        ctx.write(msg, promise);
    }
}
```

```java
pipeline.addLast(new FlowLogHandler("A"));
pipeline.addLast(new FlowLogHandler("B"));
pipeline.addLast(new FlowLogHandler("C"));
```

收到消息打印 `A inbound -> B inbound -> C inbound`；从 Channel 回写打印 `C outbound -> B outbound -> A outbound`。

| 变量 | 含义 | 为什么不能省略 |
| --- | --- | --- |
| `name` | 给日志看的 Handler 名称 | 与 `ctx.name()` 不同，这是示例自定义标签 |
| `msg` | 正在沿 Pipeline 传递的对象 | 这里只打印而不消费，所以要继续传递 |
| `promise` | 这次 write 操作的完成凭证 | 必须原样传给 `ctx.write`，否则上游无法得知写成功或失败 |
| `ctx.fireChannelRead(msg)` | 向下一个入站工位转交消息 | 忘记调用会使消息在此截断 |
| `ctx.write(msg, promise)` | 向前一个出站工位转交写请求 | 忘记调用会使响应永远到不了 Socket |

## 9.4 write 与 flush 是两个动作

`write` 把消息送入出站链并进入待写缓冲；`flush` 请求把当前可写内容推进到底层：

```java
ctx.write(responsePart1);
ctx.write(responsePart2);
ctx.flush();
```

频繁对小消息 `writeAndFlush` 会增加系统调用和小包；只 write 不 flush 又会造成客户端迟迟收不到。批量策略要兼顾延迟和吞吐。

## 9.5 异常如何传播

```java
@Override
public void exceptionCaught(
        ChannelHandlerContext ctx, Throwable cause) {
    log.warn("channel={} pipeline failed",
            ctx.channel().id().asShortText(), cause);
    ctx.close();
}
```

如果当前 Handler 不负责最终处理，应调用 `ctx.fireExceptionCaught(cause)`。异常到达 Tail 仍无人处理时通常会记录警告，但连接是否关闭取决于异常和代码路径，不能依赖“框架一定帮我关”。

## 9.6 动态修改 Pipeline

协议协商后可以动态添加、删除 Handler：

```java
if (handshakeSucceeded) {
    ChannelPipeline p = ctx.pipeline();
    p.addAfter(ctx.name(), "messageCodec", new MessageCodec());
    p.remove(this);
}
```

修改 Pipeline 应尽量在其 EventLoop 中进行。`ChannelInitializer` 在初始化新 Channel 后会自动从 Pipeline 移除。

## 9.7 Handler 设计原则

- 解码、认证、业务、编码、指标各司其职，避免“万能 Handler”。
- 入站消息若不消费就继续传播；消费引用计数对象就负责释放。
- 覆写事件方法后，若不拦截该事件，必须调用 `fireXxx` 或 `super`。
- 连接状态不要放在可共享 Handler 的普通可变字段。
- 日志应包含 Channel ID、远端地址、消息类型和 requestId，避免直接打印敏感正文。

## 9.8 官方 API

- [ChannelPipeline](https://netty.io/4.1/api/io/netty/channel/ChannelPipeline.html)
- [ChannelHandlerContext](https://netty.io/4.1/api/io/netty/channel/ChannelHandlerContext.html)
- [ChannelInboundHandler](https://netty.io/4.1/api/io/netty/channel/ChannelInboundHandler.html)
- [ChannelOutboundHandler](https://netty.io/4.1/api/io/netty/channel/ChannelOutboundHandler.html)
- [ChannelDuplexHandler](https://netty.io/4.1/api/io/netty/channel/ChannelDuplexHandler.html)
- [ChannelPromise](https://netty.io/4.1/api/io/netty/channel/ChannelPromise.html)

## 9.9 知识问答

**问：为什么入站解码器按添加顺序执行，出站编码器却反向？**

答：Pipeline 是双向链。网络数据从 Head 进入，业务写出从 Tail 回到 Head，编码顺序自然与解码相反。

**问：忘记调用 `ctx.fireChannelRead(msg)` 会怎样？**

答：消息被当前 Handler 截断，后续 Handler 收不到；若 msg 需要引用计数且也没释放，还会泄漏。

**问：`ctx.writeAndFlush` 一定比 `channel.writeAndFlush` 快吗？**

答：它可能少经过一些 Handler，但正确性优先。跳过必要编码器会把错误类型直接交给 transport，通常导致 `UnsupportedOperationException`。

**问：可以给每个 Channel 动态换协议吗？**

答：可以，例如 TLS/HTTP 升级或登录后切换协议，但要明确未处理字节、Handler 顺序和并发修改时机。

### 动手题

把 StringEncoder 放到业务 Handler 的后面，分别使用 `ctx.writeAndFlush(String)` 和 `ctx.channel().writeAndFlush(String)`，解释其中一种为何绕过编码器。

------

上一章：[[08-ByteBuf与引用计数]]　下一章：[[10-粘包拆包与自定义协议]]
