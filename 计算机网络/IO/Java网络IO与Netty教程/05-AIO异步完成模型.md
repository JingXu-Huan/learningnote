# 五、AIO 异步完成模型

## 5.1 从“就绪”到“完成”

NIO/Reactor 通知“Channel 可以读了”，应用再执行 `read`；AIO 发起读取后先返回，操作完成时通过 `CompletionHandler` 或 `Future` 交付结果。

```text
Reactor：就绪通知 -> 应用执行 IO -> 处理数据
Proactor：应用发起 IO -> 完成通知（带结果）-> 处理数据
```

Java AIO 的 API 表达的是完成模型，但不同操作系统/JDK provider 的底层实现不一定都是同一种内核异步机制。

## 5.2 教程代码：异步 Echo Server

完整代码：`示例代码/src/main/java/note/io/aio/AioEchoServer.java`。

```java
AsynchronousServerSocketChannel server =
        AsynchronousServerSocketChannel.open()
                .bind(new InetSocketAddress(9002));

server.accept(null, new CompletionHandler<>() {
    @Override
    public void completed(AsynchronousSocketChannel client, Object attachment) {
        server.accept(null, this); // 先继续接收下一条连接
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        readAgain(client, buffer);
    }

    @Override
    public void failed(Throwable error, Object attachment) {
        error.printStackTrace();
    }
});
```

读取也必须连续发起，完成一次读取并不代表消息结束：

```java
private static void readAgain(
        AsynchronousSocketChannel client, ByteBuffer buffer) {
    client.read(buffer, buffer, new CompletionHandler<>() {
        @Override
        public void completed(Integer n, ByteBuffer buf) {
            if (n == -1) {
                close(client);
                return;
            }
            buf.flip();
            client.write(buf, buf, new CompletionHandler<>() {
                @Override
                public void completed(Integer written, ByteBuffer pending) {
                    if (pending.hasRemaining()) {
                        client.write(pending, pending, this);
                    } else {
                        pending.clear();
                        readAgain(client, pending);
                    }
                }

                @Override
                public void failed(Throwable e, ByteBuffer pending) {
                    close(client);
                }
            });
        }

        @Override
        public void failed(Throwable e, ByteBuffer buf) {
            close(client);
        }
    });
}
```

回调中仍要处理 EOF、部分写、协议边界和异常关闭。异步 API 没有消除网络状态，只改变了组织控制流的方式。

## 5.3 CompletionHandler 与 Future

`Future<Integer> future = channel.read(buffer)` 可以轮询或阻塞 `get`；如果立刻 `get`，又把异步 API 写成了阻塞流程。`CompletionHandler` 更符合事件驱动，但嵌套回调要通过状态对象或 `CompletableFuture` 组合控制复杂度。

## 5.4 为什么 Netty 主流使用 NIO

- Netty 以统一的 EventLoop、Pipeline 和 Future 模型封装多种 transport。
- Java AIO 在不同平台的实现特征与成熟度不完全相同。
- Reactor 线程模型更容易与 Netty 的有序事件传播、任务队列和背压机制整合。

这不是“AIO 理论性能一定差”，而是框架生态、跨平台行为和工程控制之间的选择。

## 5.5 官方 API

- [AsynchronousServerSocketChannel](https://www.apiref.com/java11-zh/java.base/java/nio/channels/AsynchronousServerSocketChannel.html)
- [AsynchronousSocketChannel](https://www.apiref.com/java11-zh/java.base/java/nio/channels/AsynchronousSocketChannel.html)
- [CompletionHandler](https://www.apiref.com/java11-zh/java.base/java/nio/channels/CompletionHandler.html)
- [AsynchronousChannelGroup](https://www.apiref.com/java11-zh/java.base/java/nio/channels/AsynchronousChannelGroup.html)

## 5.6 知识问答

**问：AIO 回调在哪个线程执行？**

答：由关联的 `AsynchronousChannelGroup` 和 provider 调度。不能假定它是发起 IO 的线程，也不应在回调里执行长时间阻塞任务。

**问：调用一次 `accept` 后是否会持续接收？**

答：不会。每次完成只接收一条连接，通常要在 `completed` 中再次调用 `accept`。

**问：AIO 是否不用考虑线程安全？**

答：仍要考虑。多个完成回调、业务线程和关闭流程可能访问同一连接状态，应明确所有权或串行化执行。

### 动手题

给 AIO Echo 增加“4 字节长度 + 正文”的累积解码状态。思考为什么状态应该绑定连接，而不是定义成静态共享 Buffer。

------

上一章：[[04-Selector与手写Reactor]]　下一章：[[06-Netty入门与第一个Echo服务]]
