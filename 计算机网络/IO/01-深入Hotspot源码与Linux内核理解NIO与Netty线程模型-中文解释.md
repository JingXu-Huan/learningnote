# Java BIO、NIO、AIO 与 Netty 线程模型中文解释

> 对应资料：`01-深入Hotspot源码与Linux内核理解NIO与Netty线程模型-预习资料.pdf`
>
> 本文重点处理原 PDF 中代码截图缩进不明显、分页断裂和注释不足的问题。

这份资料的主线是：

```text
BIO：一个连接通常占用一个阻塞线程
  -> NIO：一个线程通过 Selector 管理多个连接
  -> epoll：把大量 IO 就绪判断交给 Linux 内核
  -> AIO：IO 完成后由系统回调通知程序
  -> Netty：在 NIO 之上封装出更完整的异步网络框架
```

需要先说明：PDF 中的代码主要是教学示例，不是完整的生产级服务器。示例中省略了资源关闭、半包处理、部分写入、异常连接、线程安全和协议解析等细节。本文会把这些缺口标出来。

---

## 一、先区分四个概念

阅读 BIO、NIO、AIO 时，最容易混淆的是“同步/异步”和“阻塞/非阻塞”。它们描述的不是同一个维度。

### 阻塞与非阻塞：调用线程是否需要等待

- 阻塞：调用方法后，如果暂时没有结果，当前线程停在那里等待。
- 非阻塞：调用方法后，如果暂时没有结果，立即返回，当前线程可以继续做其他事情。

### 同步与异步：谁负责确认操作完成

- 同步：调用方主动调用 `read`、`write` 等方法，并负责确认操作结果。
- 异步：调用方提交操作后，操作系统或运行时在操作完成时通过回调、Future 等方式通知调用方。

可以用下面的表格记忆：

| 模型 | 调用方式 | 典型等待方式 |
|---|---|---|
| 同步阻塞 | 主动调用，没数据就等待 | 线程卡在 `read()` 或 `accept()` |
| 同步非阻塞 | 主动调用，没有结果就立即返回 | 程序反复检查或等待 Selector 事件 |
| 异步非阻塞 | 提交操作，不主动等待结果 | 完成回调通知 |

“异步阻塞”在概念上也可以组合出来，但网络服务器中最常讨论的是 BIO、NIO 和 AIO。

---

## 二、BIO：Blocking IO

BIO 是同步阻塞 IO。最直观的模型是：

```text
客户端 1 -> 服务端线程 1
客户端 2 -> 服务端线程 2
客户端 3 -> 服务端线程 3
```

如果一个客户端长时间不发送数据，对应线程就会阻塞在 `read()` 上。这个线程无法有效处理其他客户端。

### 2.1 PDF 中 BIO 服务端代码的含义

原 PDF 的服务端代码可以整理成下面这样：

```java
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(9000);

        while (true) {
            System.out.println("等待连接...");

            // 阻塞等待客户端连接
            Socket clientSocket = serverSocket.accept();
            System.out.println("有客户端连接了...");

            // 当前线程直接处理客户端
            handler(clientSocket);
        }
    }

    private static void handler(Socket clientSocket) throws IOException {
        byte[] bytes = new byte[1024];
        System.out.println("准备 read...");

        // 没有数据时阻塞；返回 -1 表示对端关闭输入
        int read = clientSocket.getInputStream().read(bytes);
        System.out.println("read 完毕...");

        if (read != -1) {
            // 只能使用 0 到 read 的有效字节
            String message = new String(bytes, 0, read);
            System.out.println("接收到客户端的数据：" + message);
        }

        clientSocket.getOutputStream().write("HelloClient".getBytes());
        clientSocket.getOutputStream().flush();
    }
}
```

这里有两个阻塞点：

1. `serverSocket.accept()`：没有新客户端时，服务端线程等待；
2. `inputStream.read(bytes)`：客户端连接已建立，但没有发送数据时，处理线程等待。

### 2.2 为每个客户端创建线程

为了让服务端同时处理多个客户端，PDF 还给出了线程版本的思路：

```java
while (true) {
    Socket clientSocket = serverSocket.accept();

    new Thread(() -> {
        try {
            handler(clientSocket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }).start();
}
```

它比单线程串行处理更实用，但代价是连接数和线程数大致一起增长。

### 2.3 BIO 客户端

```java
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketClient {
    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket("localhost", 9000)) {
            socket.getOutputStream().write(
                    "HelloServer".getBytes(StandardCharsets.UTF_8)
            );
            socket.getOutputStream().flush();

            byte[] bytes = new byte[1024];
            int read = socket.getInputStream().read(bytes);

            if (read != -1) {
                System.out.println(
                        "接收到服务端的数据："
                                + new String(bytes, 0, read, StandardCharsets.UTF_8)
                );
            }
        }
    }
}
```

原 PDF 的客户端使用 `new String(bytes)`，会把整个数组都转换成字符串，而不是只转换实际读取到的 `read` 个字节。规范写法应使用：

```java
new String(bytes, 0, read, StandardCharsets.UTF_8)
```

### 2.4 BIO 的优缺点

优点：

- 代码流程符合直觉；
- 调试容易；
- 连接数较少时足够实用。

缺点：

- 空闲连接也占用线程；
- 线程栈和上下文切换有额外成本；
- 连接数增长时，线程池和调度压力变大；
- 大量连接场景会遇到 C10K 一类的扩展性问题。

BIO 适合连接数较少、业务简单、连接生命周期较短的场景。它不一定“性能差”，而是当连接数和空闲时间上升后，线程模型会越来越浪费。

---

## 三、NIO：Non-blocking IO

NIO 在这里指 Java 的同步非阻塞网络 IO。核心思想是：

> 一个线程不再专门服务一个客户端，而是管理多个非阻塞 Channel；当某个 Channel 有事件时，再处理它。

Java NIO 的三个核心组件是：

```text
Channel  ：数据通道
Buffer   ：数据缓冲区
Selector ：多路复用器，等待多个 Channel 的事件
```

### 3.1 先不用 Selector：轮询连接列表

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

### 3.2 这个轮询版本的问题

假设有 10,000 个连接，只有 1,000 个连接此刻有数据：

- 程序仍然要调用 10,000 次 `read()`；
- 其中大部分调用都会返回 0；
- 连接数量越大，无效扫描越多。

这就是 PDF 所说的“无效遍历”。NIO 的非阻塞模式解决了线程被单个连接卡住的问题，但上面的 `List` 轮询仍然浪费 CPU。

---

## 四、Selector：只处理真正就绪的连接

Selector 可以理解为一个“事件登记处”：

1. 把 Channel 注册到 Selector；
2. 指定关心的事件，例如 `OP_ACCEPT` 或 `OP_READ`；
3. 调用 `selector.select()` 等待；
4. Selector 返回已经就绪的 Channel；
5. 只处理这些 Channel。

### 4.1 PDF 中 Selector 示例的规范排版

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

### 4.2 每一步在做什么

#### `ServerSocketChannel.open()`

创建服务端 Channel。它对应传统 BIO 中的 `ServerSocket`，但本身可以配置为非阻塞。

#### `serverSocket.bind(...)`

绑定端口。原 PDF 使用了较早的：

```java
serverSocket.socket().bind(new InetSocketAddress(9000));
```

现代 Java 中可以直接写：

```java
serverSocket.bind(new InetSocketAddress(9000));
```

#### `configureBlocking(false)`

设置为非阻塞模式。注册到 Selector 之前必须完成这一步。

#### `register(selector, SelectionKey.OP_ACCEPT)`

把服务端 Channel 注册到 Selector，并告诉 Selector：我关心新连接事件。

常见事件包括：

- `OP_ACCEPT`：有新连接可以接收；
- `OP_READ`：有数据可读；
- `OP_WRITE`：当前可以继续写入；
- `OP_CONNECT`：非阻塞连接建立过程可以继续。

#### `selector.select()`

阻塞等待事件，但这不是 BIO 中“一个线程卡在一个连接上”。它等待的是整个 Selector 管理的事件集合。只要有任意一个 Channel 就绪，`select()` 就会返回。

#### `selectedKeys()`

取得本次已经就绪的事件集合。每个 `SelectionKey` 都包含：

- 对应的 Channel；
- 注册时关注的事件；
- 当前已经就绪的事件；
- 可附加的 Handler 或连接状态对象。

#### `iterator.remove()`

表示当前事件已经从“待处理集合”消费掉。原 PDF 也特别提醒了这一点：如果不移除，后续循环可能重复处理旧 Key。

---

## 五、Buffer 的正确使用方式

原 PDF 的示例使用：

```java
ByteBuffer byteBuffer = ByteBuffer.allocate(128);
int len = socketChannel.read(byteBuffer);
System.out.println(new String(byteBuffer.array()));
```

教学上能帮助理解，但有两个问题：

1. `new String(byteBuffer.array())` 会把整个 128 字节数组都转成字符串，可能包含无效尾部数据；
2. 它没有处理半包，也没有展示 `flip()` / `get()` 的典型状态变化。

更规范的基本写法是：

```java
ByteBuffer buffer = ByteBuffer.allocate(128);
int read = channel.read(buffer);

if (read > 0) {
    // read 表示本次真正读到的字节数
    buffer.flip();

    byte[] data = new byte[buffer.remaining()];
    buffer.get(data);

    System.out.println(new String(data, StandardCharsets.UTF_8));
}
```

### `ByteBuffer` 的状态变化

```text
allocate()
    -> position = 0, limit = capacity

channel.read(buffer) / buffer.put(...)
    -> 写入数据，position 向后移动

buffer.flip()
    -> 把 position 变成 0，把原 position 变成 limit
    -> 进入读取刚才写入数据的模式

buffer.get(...) / channel.write(buffer)
    -> 读取数据，position 向后移动

buffer.clear()
    -> 准备重新使用整个 Buffer
```

如果一次读取没有得到完整请求，不能直接 `clear()`，否则未处理的半包会丢失。实际协议通常要保留未消费数据，并在下一次读取时继续拼接。

---

## 六、Java NIO 与 Linux epoll 的关系

PDF 重点解释了三个 Java 方法如何一路落到 Linux 内核：

```java
Selector selector = Selector.open();

socketChannel.register(selector, SelectionKey.OP_READ);

selector.select();
```

可以把它们理解成三个阶段：

```text
Selector.open()
    -> 创建 Java Selector
    -> Linux 下选择合适的 SelectorProvider
    -> 底层创建 epoll 实例或其他 IO 多路复用对象

channel.register(...)
    -> 把 Socket 对应的文件描述符注册到 Selector
    -> 底层通过 epoll_ctl 关注 EPOLLIN 等事件

selector.select()
    -> Java 线程进入等待
    -> 底层调用 epoll_wait
    -> 有事件就绪后返回
```

### 6.1 “文件描述符”是什么

Linux 把 Socket、文件、管道等 IO 对象抽象成文件描述符，通常简称 fd。Java 程序不会直接操作 fd，而是通过 `SocketChannel`、`Selector` 等对象间接使用它。

Java 层看到的是：

```text
SocketChannel + Selector + SelectionKey
```

Linux 层看到的是：

```text
socket fd + epoll fd + epoll_ctl + epoll_wait
```

### 6.2 `epoll_create`

```c
int epoll_create(int size);
```

创建一个 epoll 实例并返回一个文件描述符。旧版本的 `size` 参数只是容量提示，不是最大连接数；在现代 Linux 中它基本已经没有实际意义，但接口仍然保留。

### 6.3 `epoll_ctl`

```c
int epoll_ctl(
    int epfd,
    int op,
    int fd,
    struct epoll_event *event
);
```

它用于增删改某个 fd 关心的事件：

```text
EPOLL_CTL_ADD：新增监听
EPOLL_CTL_MOD：修改监听事件
EPOLL_CTL_DEL：移除监听
```

典型场景：

- 新客户端连接建立后，添加它的 fd，并关注 `EPOLLIN`；
- 输出缓冲区有数据后，修改为同时关注 `EPOLLOUT`；
- 响应写完后，取消 `EPOLLOUT`，避免持续触发写事件；
- 连接关闭时，删除 fd。

### 6.4 `epoll_event`

```c
struct epoll_event {
    __uint32_t events;
    epoll_data_t data;
};

typedef union epoll_data {
    void *ptr;
    int fd;
    __uint32_t u32;
    __uint64_t u64;
} epoll_data_t;
```

`events` 表示要监听的事件，`data` 用于携带用户数据，例如 fd 或指向连接对象的指针。

PDF 中列出的常见事件：

- `EPOLLIN`：可以读取；
- `EPOLLOUT`：可以写入；
- `EPOLLERR`：发生错误。

### 6.5 `epoll_wait`

```c
int epoll_wait(
    int epfd,
    struct epoll_event *events,
    int maxevents,
    int timeout
);
```

它等待 epoll 实例中的就绪事件。与 `select` / `poll` 每次都扫描大量 fd 不同，epoll 会维护就绪事件集合，等待返回时重点处理已经就绪的对象。

---

## 七、select、poll、epoll 的对比

PDF 给出的方向是：

| 项目 | select | poll | epoll |
|---|---|---|---|
| 事件发现 | 遍历 | 遍历 | 事件通知 |
| 内核数据结构 | 数组/位图 | 链表 | 内核维护的事件结构 |
| 每次等待的工作 | 需要扫描 fd 集合 | 需要扫描 fd 集合 | 直接返回就绪事件 |
| 规模限制 | 通常有 fd 数量上限 | 通常无固定小上限 | 通常无固定小上限 |
| 典型复杂度描述 | O(n) | O(n) | 更接近按就绪事件处理 |

这里需要稍微修正一个容易被误读的说法：不能简单把 epoll 宣传成“永远 O(1)”或“所有场景都比 poll 快”。实际成本还与就绪事件数量、触发模式、用户态处理、系统调用和连接活跃度有关。更准确的理解是：

> epoll 避免了应用每次都遍历整个连接集合，特别适合大量连接但同时活跃连接较少的场景。

PDF 所说的“回调”也不是说用户代码被 Linux 直接调用，而是内核把就绪事件放入就绪队列，`epoll_wait` 返回后由用户态线程处理。

---

## 八、Redis 的线程模型说明

PDF 以 Redis 和 Nginx 作为 epoll 事件模型的例子。教材式描述可以理解为：

```text
epoll 收集连接和读写事件
        |
        v
事件循环线程取出就绪事件
        |
        v
读取命令 -> 执行命令 -> 返回结果
```

这个例子想强调的是：大量网络连接可以由事件循环统一管理，而不是每个连接永久占用一个线程。

但不能据此得出“Redis 永远只有一个线程”这样的结论。Redis 不同版本、不同功能和不同配置下，网络 IO、命令执行、后台任务可能使用不同线程或线程池。这里应把 Redis 当作 Reactor 思想的示例，而不是当作完整的现代 Redis 实现说明。

---

## 九、AIO：Asynchronous IO

AIO 的思路与 NIO 不同：

```text
提交 read/write 操作
        |
        v
调用方继续执行，不主动等待
        |
        v
操作系统完成 IO
        |
        v
CompletionHandler 回调通知
```

在 Java 中，AIO 也常叫 NIO.2，主要 API 是：

- `AsynchronousServerSocketChannel`；
- `AsynchronousSocketChannel`；
- `CompletionHandler`。

### 9.1 AIO 服务端的规范排版

PDF 中的 AIO 服务端代码可以整理为：

```java
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;

public class AIOServer {
    public static void main(String[] args) throws Exception {
        AsynchronousServerSocketChannel server =
                AsynchronousServerSocketChannel.open()
                        .bind(new InetSocketAddress(9000));

        server.accept(null,
                new CompletionHandler<AsynchronousSocketChannel, Void>() {
                    @Override
                    public void completed(
                            AsynchronousSocketChannel client,
                            Void attachment
                    ) {
                        // 必须再次调用 accept，继续接收后续客户端
                        server.accept(null, this);

                        try {
                            System.out.println(client.getRemoteAddress());

                            ByteBuffer buffer = ByteBuffer.allocate(1024);

                            client.read(buffer, buffer,
                                    new CompletionHandler<Integer, ByteBuffer>() {
                                        @Override
                                        public void completed(
                                                Integer result,
                                                ByteBuffer attachment
                                        ) {
                                            if (result == -1) {
                                                return;
                                            }

                                            attachment.flip();

                                            String message = StandardCharsets.UTF_8
                                                    .decode(attachment)
                                                    .toString();
                                            System.out.println(message);

                                            client.write(ByteBuffer.wrap(
                                                    "HelloClient"
                                                            .getBytes(StandardCharsets.UTF_8)
                                            ));
                                        }

                                        @Override
                                        public void failed(
                                                Throwable error,
                                                ByteBuffer attachment
                                        ) {
                                            error.printStackTrace();
                                        }
                                    });
                        } catch (Exception error) {
                            error.printStackTrace();
                        }
                    }

                    @Override
                    public void failed(Throwable error, Void attachment) {
                        error.printStackTrace();
                    }
                });

        // 示例程序需要保持进程存活，生产程序应有正规的生命周期管理
        Thread.sleep(Long.MAX_VALUE);
    }
}
```

原 PDF 中的代码有一个很重要的提示：在 `completed` 回调中必须再次调用 `serverChannel.accept(...)`。如果只接收一次，服务端处理完第一个客户端后，就不会继续监听后续连接。

### 9.2 AIO 客户端的规范排版

```java
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;

public class AIOClient {
    public static void main(String[] args) throws Exception {
        try (AsynchronousSocketChannel client =
                     AsynchronousSocketChannel.open()) {

            // connect() 返回 Future，get() 会等待连接完成
            client.connect(new InetSocketAddress("127.0.0.1", 9000)).get();

            client.write(ByteBuffer.wrap(
                    "HelloServer".getBytes(StandardCharsets.UTF_8)
            )).get();

            ByteBuffer buffer = ByteBuffer.allocate(512);
            int read = client.read(buffer).get();

            if (read != -1) {
                buffer.flip();
                System.out.println(StandardCharsets.UTF_8.decode(buffer));
            }
        }
    }
}
```

这里虽然使用了异步 API，但客户端调用 `.get()` 等待 Future 结果，因此这个客户端代码的调用方式又表现出同步等待的特征。AIO 的优势要在服务端以回调或其他非阻塞方式组织后续逻辑时才能体现出来。

---

## 十、为什么 Netty 通常使用 NIO 而不是 AIO

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

## 十一、BIO、NIO、AIO 对比

PDF 中的表格可以这样理解：

| 对比项 | BIO | NIO | AIO |
|---|---|---|---|
| IO 形式 | 同步阻塞 | 同步非阻塞 | 异步非阻塞 |
| 典型机制 | 一个线程阻塞等待一个连接 | Selector 等待多个 Channel | 提交操作，完成后回调 |
| 编程难度 | 较低 | 较高 | 较高 |
| 资源利用率 | 连接多时较低 | 大量连接时较好 | 取决于平台和实现 |
| 适合场景 | 连接少、代码简单 | 连接多、IO 事件频繁 | 适合异步完成通知模型 |
| Java 主要 API | `Socket` | `Channel`、`Buffer`、`Selector` | `AsynchronousChannel`、`CompletionHandler` |

不能只按“BIO 低、NIO/AIO 高”评价吞吐量。实际性能还受以下因素影响：

- 业务计算量；
- 消息大小；
- 连接是否长连接；
- 活跃连接比例；
- 内存分配和复制；
- 线程池配置；
- 操作系统和 JDK 实现；
- 是否存在数据库或远程 RPC 阻塞。

---

## 十二、“煮水”比喻到底在说明什么

PDF 最后用“老张煮水”区分同步/异步和阻塞/非阻塞。

### 同步阻塞

普通水壶放到火上，老张一直站在那里等水开。

```text
老张发起操作 -> 一直等待 -> 水开后继续
```

对应网络中的：

```java
socket.read(buffer); // 当前线程一直等
```

### 同步非阻塞

普通水壶放到火上，老张去看电视，但需要不断去厨房查看水开没开。

```text
老张发起操作 -> 主动反复检查 -> 发现完成
```

对应网络中的：

```java
while (true) {
    int read = channel.read(buffer);
    if (read > 0) {
        break;
    }
    // 没有数据，继续做其他事情或等待下一次检查
}
```

Selector 相比这种“扫描全部连接”的方式更高效，因为它让操作系统帮忙筛选出真正就绪的连接。

### 异步非阻塞

会响的水壶放到火上，老张去看电视，不再主动检查；水开后水壶自己响。

```text
老张提交操作 -> 去做其他事情 -> 完成回调通知
```

对应 AIO 的 `CompletionHandler`。

这个比喻的重点是：

- “同步/异步”看的是任务完成后由谁通知；
- “阻塞/非阻塞”看的是发起调用的人是否需要等待。

---

## 十三、原 PDF 示例代码需要注意的坑

### 1. 一次 `read()` 不等于一条完整消息

TCP 是字节流，不保留消息边界。一次 `read()` 可能得到：

- 半条消息；
- 一条完整消息；
- 多条消息拼在一起。

实际程序必须设计协议，例如：

```text
固定长度消息
长度字段 + 消息体
分隔符 + 消息体
```

### 2. 只能处理有效字节

不要直接这样写：

```java
new String(buffer.array())
```

应该使用实际读取长度：

```java
new String(buffer.array(), 0, read, StandardCharsets.UTF_8)
```

或者使用 `flip()` 和 `buffer.get()`。

### 3. `read() == 0` 不是连接关闭

在非阻塞模式下：

- `read() > 0`：读到了数据；
- `read() == 0`：现在没有可读数据；
- `read() == -1`：对端关闭连接。

### 4. `write()` 也可能只写一部分

如果响应没有一次写完，要保留剩余数据，并继续关注 `OP_WRITE`。没有待发送数据时，不要一直注册 `OP_WRITE`，否则容易导致事件循环空转。

### 5. Buffer 不能随意清空

半包数据必须保存。处理完已经消费的部分后，可以用 `compact()` 把未处理数据移到开头，再继续读取。

### 6. `Selector` 不是业务线程池

Selector 线程应该快速完成 accept、read、write 和状态切换。数据库查询、文件 IO、远程调用和复杂计算应交给业务线程池，否则一个慢任务会拖住所有连接。

### 7. `selector.select()` 不是“异步执行”

NIO 的 `select()` 仍然由当前线程主动等待和处理事件，所以它通常归类为同步非阻塞 IO。AIO 才是提交操作后由完成通知驱动后续逻辑。

### 8. Windows 不能直接套用 Linux epoll 细节

Java NIO 会根据平台选择底层实现。Linux 常见的是 epoll，Windows 使用 Winsock 相关机制。应用程序应使用 Java NIO API，而不是把 Linux 的 epoll fd、系统调用和数据结构直接写进跨平台 Java 代码。

---

## 十四、从 HotSpot 到内核，应该怎样理解

PDF 想表达的调用链可以简化为：

```text
Java 业务代码
    |
    v
SocketChannel / Selector
    |
    v
JDK NIO 实现与 SelectorProvider
    |
    v
Native 方法与平台 IO API
    |
    v
Linux epoll / Windows Winsock 等机制
    |
    v
网卡、中断、内核就绪队列
```

这里的“异步通知”不是说 Java 线程完全不参与，而是说：

- 操作系统负责监测大量 fd 的就绪状态；
- Java 线程不必逐个扫描所有连接；
- `select()` / `epoll_wait()` 返回就绪事件；
- Java 线程继续完成用户态的数据读取和业务处理。

所以，NIO 的优势不是“Java 不做任何工作”，而是把最浪费的连接状态扫描交给了更适合做这件事的内核机制。

---

## 十五、与 Netty 线程模型的对应关系

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

## 十六、最终总结

### BIO

BIO 代码最简单，但每个阻塞连接通常需要一个线程。连接少时非常合适，连接多且空闲时间长时资源浪费明显。

### NIO

NIO 通过非阻塞 Channel 和 Selector，让一个线程管理多个连接。它解决了“一连接一线程”的问题，但需要程序员自己处理状态、半包和部分写入。

### epoll

Linux epoll 是 NIO 在 Linux 上常见的底层实现之一。它帮助内核维护就绪事件，减少应用每轮遍历全部连接的成本。

### AIO

AIO 由程序提交 IO 操作，操作完成后通过回调通知。它的编程模型更异步，但平台实现、可控性和工程生态需要具体评估。

### Netty

Netty 不是简单地“把 NIO API 换个名字”，而是在 NIO 之上封装了事件循环、Pipeline、Buffer、协议处理、线程模型和异常生命周期管理。

如果只记住一句话：

> BIO 是线程等待 IO，NIO 是线程等待就绪事件，AIO 是提交 IO 后等待完成通知；Netty 则把 NIO 的复杂工程细节封装成可复用的网络编程模型。
