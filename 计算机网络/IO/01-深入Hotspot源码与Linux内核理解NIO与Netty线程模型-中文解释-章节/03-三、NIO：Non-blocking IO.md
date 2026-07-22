# 三、NIO：Non-blocking IO

NIO 在这里指 Java 的同步非阻塞网络 IO。核心思想是：

> 一个线程不再专门服务一个客户端，而是管理多个非阻塞 Channel；当某个 Channel 有事件时，再处理它。

Java NIO 的三个核心组件是：

```text
Channel  ：数据通道
Buffer   ：数据缓冲区
Selector ：多路复用器，等待多个 Channel 的事件
```

## 3.1 先不用 Selector：轮询连接列表

PDF 先给出一个过渡版本，把所有客户端连接放入 `List`，然后逐个尝试读取：

```java
ServerSocketChannel serverSocket = ServerSocketChannel.open();
serverSocket.bind(new InetSocketAddress(9000));
serverSocket.configureBlocking(false);

List<SocketChannel> channelList = new ArrayList<>();

while (true) {
    // 非阻塞 accept：没有新连接时返回 null
    SocketChannel socketChannel = serverSocket.accept();

    if (socketChannel != null) {
        socketChannel.configureBlocking(false);
        channelList.add(socketChannel);
    }

    // 每次都扫描全部连接
    Iterator<SocketChannel> iterator = channelList.iterator();
    while (iterator.hasNext()) {
        SocketChannel channel = iterator.next();
        ByteBuffer buffer = ByteBuffer.allocate(128);

        // 非阻塞读取：没有数据时返回 0
        int read = channel.read(buffer);

        if (read > 0) {
            // 必须只读取有效的 read 个字节
            String message = new String(
                    buffer.array(), 0, read, StandardCharsets.UTF_8
            );
            System.out.println("接收到消息：" + message);
        } else if (read == -1) {
            // 对端关闭连接，从集合删除
            iterator.remove();
            channel.close();
        }
    }
}
```

## 3.2 这个轮询版本的问题

假设有 10,000 个连接，只有 1,000 个连接此刻有数据：

- 程序仍然要调用 10,000 次 `read()`；
- 其中大部分调用都会返回 0；
- 连接数量越大，无效扫描越多。

这就是 PDF 所说的“无效遍历”。NIO 的非阻塞模式解决了线程被单个连接卡住的问题，但上面的 `List` 轮询仍然浪费 CPU。

---

