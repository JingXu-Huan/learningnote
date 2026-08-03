# 八、ByteBuf 与引用计数

## 8.1 为什么 Netty 不只使用 ByteBuffer

`ByteBuf` 为网络场景提供：

- 独立的 `readerIndex`、`writerIndex`，不需要 `flip`；
- 动态扩容和容量上限；
- 堆内/直接内存、池化/非池化统一接口；
- 零复制视图：`slice`、`duplicate`、`CompositeByteBuf`；
- 引用计数管理池化和直接内存生命周期。

```text
discardable | readable bytes | writable bytes
0      readerIndex       writerIndex       capacity
```

## 8.2 教程代码：双游标

```java
ByteBuf buf = Unpooled.buffer(16);
buf.writeInt(3);
buf.writeCharSequence("cat", StandardCharsets.UTF_8);

System.out.println(buf.readerIndex());   // 0
System.out.println(buf.writerIndex());   // 7

int length = buf.readInt();
String body = buf.readCharSequence(
        length, StandardCharsets.UTF_8).toString();
System.out.println(body);                // cat
buf.release();
```

`getInt(index)` 是绝对读取，不移动 `readerIndex`；`readInt()` 是相对读取，会推进游标。协议解码器常用 `getInt` 偷看长度，再判断完整帧是否到齐。

## 8.3 分配策略

```java
ByteBuf ioBuffer = ctx.alloc().ioBuffer(1024);
ByteBuf heap = ctx.alloc().heapBuffer(1024);
ByteBuf direct = ctx.alloc().directBuffer(1024);
```

优先通过 `ctx.alloc()` 使用 Channel 配置的分配器。不要在业务热路径随意混用 `Unpooled`，否则会绕开池化策略。

## 8.4 引用计数所有权

`refCnt() > 0` 才能访问；`release()` 减 1，到 0 后内存被回收/归还池；之后访问会抛 `IllegalReferenceCountException`。

```java
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
    try {
        ByteBuf buf = (ByteBuf) msg;
        consume(buf);
    } finally {
        ReferenceCountUtil.release(msg);
    }
}
```

如果把消息继续传播，则不要在当前 Handler 释放：

```java
ctx.fireChannelRead(msg); // 所有权交给下一个入站 Handler
```

如果跨出当前回调异步保存，必须先保留：

```java
ByteBuf retained = ((ByteBuf) msg).retainedDuplicate();
businessExecutor.execute(() -> {
    try {
        consume(retained);
    } finally {
        retained.release();
    }
});
ReferenceCountUtil.release(msg);
```

更安全的做法是尽早把 ByteBuf 解码为普通不可变业务对象，避免引用计数跨线程传播。

## 8.5 SimpleChannelInboundHandler 的自动释放

`SimpleChannelInboundHandler<T>` 默认在 `channelRead0` 返回后释放匹配类型的消息：

```java
protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
    // 正常情况下这里不要再 msg.release()
}
```

若要把 `msg` 继续传给后续 Handler，需要 `retain`：

```java
ctx.fireChannelRead(msg.retain());
```

`ChannelInboundHandlerAdapter` 不会自动释放，所有权由代码自行处理。混淆两者会导致泄漏或重复释放。

## 8.6 slice、duplicate 与 copy

| API | 共享底层内存 | 独立游标 | 自动增加引用计数 |
| --- | --- | --- | --- |
| `slice` | 是 | 是 | 否 |
| `retainedSlice` | 是 | 是 | 是 |
| `duplicate` | 是 | 是 | 否 |
| `copy` | 否 | 是 | 新缓冲区自己的计数 |

“共享内容但没 retain”的视图最容易在父缓冲区释放后失效。

## 8.7 泄漏检测

开发/测试环境可用：

```powershell
$env:JAVA_TOOL_OPTIONS='-Dio.netty.leakDetection.level=paranoid'
```

`paranoid` 成本较高，用于定位测试；生产一般使用默认采样或根据问题临时调整。泄漏报告中的访问记录用于寻找“最后一次接触却未释放”的代码路径。

## 8.8 官方 API

- [ByteBuf](https://netty.io/4.1/api/io/netty/buffer/ByteBuf.html)
- [ByteBufAllocator](https://netty.io/4.1/api/io/netty/buffer/ByteBufAllocator.html)
- [ReferenceCounted](https://netty.io/4.1/api/io/netty/util/ReferenceCounted.html)
- [ReferenceCountUtil](https://netty.io/4.1/api/io/netty/util/ReferenceCountUtil.html)
- [SimpleChannelInboundHandler](https://netty.io/4.1/api/io/netty/channel/SimpleChannelInboundHandler.html)
- [ResourceLeakDetector.Level](https://netty.io/4.1/api/io/netty/util/ResourceLeakDetector.Level.html)

## 8.9 知识问答

**问：`writeAndFlush(buf)` 后还能修改或释放 buf 吗？**

答：通常不能再把它当作自己持有。出站操作接管消息并在完成后释放；若确需保留独立所有权，应复制或正确 retain，并明确对应 release。

**问：Direct ByteBuf 是否一定快？**

答：不一定。它通常有利于 Socket IO，但分配成本、业务访问模式和小对象数量都会影响结果。池化能显著改变结论，必须压测。

**问：`readSlice` 为什么危险？**

答：它共享父缓冲区且不增加引用计数；切片活得比父缓冲区久时会访问已释放内存。跨回调使用通常选 `readRetainedSlice` 或复制。

**问：垃圾回收能兜底 ByteBuf 泄漏吗？**

答：不能把 GC 当生命周期管理。池化/直接内存需要及时归还，泄漏会导致池耗尽、直接内存增长和性能恶化。

### 动手题

用 `EmbeddedChannel` 向一个 `ChannelInboundHandlerAdapter` 写入 ByteBuf，故意不传播也不释放，开启 paranoid 检测；再用 `try/finally` 修复。

------

上一章：[[07-EventLoop与线程模型]]　下一章：[[09-Pipeline与Handler事件传播]]

