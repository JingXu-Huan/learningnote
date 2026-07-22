# 六、Reactor 第一步：创建 Selector 和监听 Channel

PDF 中的初始化代码如下，下面加入了中文注释，并把较老的写法保留为原意：

```java
class Reactor implements Runnable {
    // Selector 负责等待多个 Channel 的 IO 就绪事件
    private final Selector selector;

    // ServerSocketChannel 负责接收新的 TCP 连接
    private final ServerSocketChannel serverSocket;

    Reactor(int port) throws IOException {
        // 创建 Selector
        selector = Selector.open();

        // 创建服务端监听 Channel
        serverSocket = ServerSocketChannel.open();

        // 绑定监听端口
        serverSocket.socket().bind(new InetSocketAddress(port));

        // 注册到 Selector 之前，必须设置为非阻塞模式
        serverSocket.configureBlocking(false);

        // 只关心“有新连接到达”这一类事件
        SelectionKey key = serverSocket.register(
                selector,
                SelectionKey.OP_ACCEPT
        );

        // 当 OP_ACCEPT 就绪时，Selector 会取出这个 Acceptor 执行
        key.attach(new Acceptor());
    }
}
```

这里有三个重要对象：

- `ServerSocketChannel`：服务端监听端口；
- `Selector`：等待多个 Channel 的事件；
- `SelectionKey`：表示“某个 Channel 在某个 Selector 上注册了哪些事件”，还可以附加一个 Handler。

## 为什么要先设置非阻塞模式

`Selector` 的前提是 Channel 不应该因为一次读写而长时间阻塞。正确顺序通常是：

```java
channel.configureBlocking(false);
channel.register(selector, events);
```

阻塞 Channel 不能直接用于 Selector 注册。

---

