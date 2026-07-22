# 四、Selector：只处理真正就绪的连接

Selector 可以理解为一个“事件登记处”：

1. 把 Channel 注册到 Selector；
2. 指定关心的事件，例如 `OP_ACCEPT` 或 `OP_READ`；
3. 调用 `selector.select()` 等待；
4. Selector 返回已经就绪的 Channel；
5. 只处理这些 Channel。

## 4.1 PDF 中 Selector 示例的规范排版

下面是根据 PDF 重排后的版本。这个示例只做“接收连接”和“读取打印”，并没有实现完整的协议或响应发送。

```java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

public class NioSelectorServer {
    public static void main(String[] args) throws IOException {
        // 1. 创建服务端监听 Channel
        ServerSocketChannel serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(9000));

        // 2. 设置为非阻塞模式
        serverSocket.configureBlocking(false);

        // 3. 创建 Selector
        Selector selector = Selector.open();

        // 4. 让 Selector 关注“新连接到达”事件
        serverSocket.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("服务启动成功");

        while (true) {
            // 阻塞等待至少一个事件就绪
            selector.select();

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                // 处理完当前 key 后立即移除，避免下次重复处理
                iterator.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    accept(selector, key);
                } else if (key.isReadable()) {
                    read(key);
                }
            }
        }
    }

    private static void accept(Selector selector, SelectionKey key)
            throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();

        // 非阻塞模式下理论上可能返回 null，稳妥起见仍然判断
        SocketChannel client = server.accept();
        if (client == null) {
            return;
        }

        client.configureBlocking(false);

        // 当前示例只关注读事件，没有待发送数据时不要监听 OP_WRITE
        client.register(selector, SelectionKey.OP_READ);
        System.out.println("客户端连接成功");
    }

    private static void read(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(128);

        int read = client.read(buffer);

        if (read > 0) {
            String message = new String(
                    buffer.array(), 0, read, StandardCharsets.UTF_8
            );
            System.out.println("接收到消息：" + message);
        } else if (read == -1) {
            System.out.println("客户端断开连接");
            key.cancel();
            client.close();
        }
    }
}
```

## 4.2 每一步在做什么

### `ServerSocketChannel.open()`

创建服务端 Channel。它对应传统 BIO 中的 `ServerSocket`，但本身可以配置为非阻塞。

### `serverSocket.bind(...)`

绑定端口。原 PDF 使用了较早的：

```java
serverSocket.socket().bind(new InetSocketAddress(9000));
```

现代 Java 中可以直接写：

```java
serverSocket.bind(new InetSocketAddress(9000));
```

### `configureBlocking(false)`

设置为非阻塞模式。注册到 Selector 之前必须完成这一步。

### `register(selector, SelectionKey.OP_ACCEPT)`

把服务端 Channel 注册到 Selector，并告诉 Selector：我关心新连接事件。

常见事件包括：

- `OP_ACCEPT`：有新连接可以接收；
- `OP_READ`：有数据可读；
- `OP_WRITE`：当前可以继续写入；
- `OP_CONNECT`：非阻塞连接建立过程可以继续。

### `selector.select()`

阻塞等待事件，但这不是 BIO 中“一个线程卡在一个连接上”。它等待的是整个 Selector 管理的事件集合。只要有任意一个 Channel 就绪，`select()` 就会返回。

### `selectedKeys()`

取得本次已经就绪的事件集合。每个 `SelectionKey` 都包含：

- 对应的 Channel；
- 注册时关注的事件；
- 当前已经就绪的事件；
- 可附加的 Handler 或连接状态对象。

### `iterator.remove()`

表示当前事件已经从“待处理集合”消费掉。原 PDF 也特别提醒了这一点：如果不移除，后续循环可能重复处理旧 Key。

---

