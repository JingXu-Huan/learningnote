# 十、为什么 Netty 通常使用 NIO 而不是 AIO

PDF 的观点是：Netty 在 Linux 上主要选择 NIO，原因包括：

1. Linux 上 Java AIO 的底层实现和平台支持在较长时间内并不理想；
2. AIO 的语义和实现细节不容易做跨平台一致的高性能封装；
3. NIO + Selector 已经能提供成熟的事件循环模型；
4. Netty 可以在 NIO 之上补足协议解析、线程调度、内存管理和 Pipeline 等能力。

需要把这段话理解为资料形成时期的工程判断，而不是绝对规律。不同操作系统、JDK 版本和业务类型可能有不同表现，最终仍应通过基准测试验证。

Netty 选择 NIO 后，自己解决了大量底层问题，例如：

- `ByteBuffer` 使用不方便，封装为更好用的 `ByteBuf`；
- Selector 事件循环重复代码多，封装为 `EventLoop`；
- 连接处理逻辑分散，封装为 `ChannelPipeline`；
- 半包、粘包和协议解码，交给 Decoder/Handler；
- Boss 和 Worker 的线程分工，交给 `EventLoopGroup`；
- 连接关闭、异常传播和生命周期，交给统一的 Channel 模型。

---

