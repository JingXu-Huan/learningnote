# 十五、与 Netty 线程模型的对应关系

这份 PDF 讲的是 Netty 依赖的底层思想。可以用下面的对应关系连接起来：

| PDF / Java NIO 概念 | Netty 概念 |
|---|---|
| `Selector` | `NioEventLoop` 内部的事件循环 |
| `ServerSocketChannel` | `NioServerSocketChannel` |
| `SocketChannel` | `NioSocketChannel` |
| `SelectionKey` 附件 | `ChannelPipeline` 和 Handler |
| `ByteBuffer` | `ByteBuf` |
| 接收连接的 Acceptor | Boss EventLoop |
| 处理客户端 IO 的 Reactor | Worker EventLoop |
| 业务线程池 | 独立业务 Executor |

典型的 Netty 服务端结构是：

```text
Boss EventLoop
    -> 接收连接
    -> 把连接注册到某个 Worker EventLoop

Worker EventLoop
    -> 监听读写事件
    -> 触发 Pipeline 中的 Handler

业务线程池
    -> 执行不能放在 EventLoop 中的慢任务
```

最重要的工程原则是：

> 不要在 EventLoop 线程中执行长时间阻塞任务。

否则，即使底层使用 epoll，整个事件循环仍会因为业务代码变慢而失去响应能力。

---

