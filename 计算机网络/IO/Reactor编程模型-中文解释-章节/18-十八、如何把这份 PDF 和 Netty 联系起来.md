# 十八、如何把这份 PDF 和 Netty 联系起来

这份 PDF 讲的是 Reactor 的底层思想；Netty 是对这套思想的工程化封装。

大致对应关系如下：

| PDF 中的概念 | Netty 中的常见对应物 |
|---|---|
| Selector | `NioEventLoop` 内部的事件循环 |
| ServerSocketChannel | `NioServerSocketChannel` |
| SocketChannel | `NioSocketChannel` |
| SelectionKey attachment | `ChannelPipeline` / Handler 链 |
| ByteBuffer | Netty 的 `ByteBuf` |
| Acceptor | `ServerBootstrap` 的 Boss EventLoop |
| Sub Reactor | Worker EventLoop |
| Worker 线程池 | 业务线程池或异步任务执行器 |

学习 Netty 时，建议先搞清楚这份 PDF 中的四件事：

1. 谁负责等待 IO 事件；
2. 谁负责接收连接；
3. 谁负责读取和写回网络数据；
4. 慢业务为什么不能占用 EventLoop。

理解这四点后，再看 Netty 的 `EventLoopGroup`、`ChannelPipeline`、`ChannelHandler` 和 `ByteBuf` 会容易很多。

---

