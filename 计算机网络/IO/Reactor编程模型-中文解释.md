# Reactor 编程模型中文解释

> 对应资料：`Reactor编程模型-预习资料ne.pdf`
>
> 原文标题：*Scalable IO in Java*
>
> 作者：Doug Lea，State University of New York at Oswego
>
> 原文主页：<http://gee.cs.oswego.edu>

这份 PDF 不是一篇从头到尾完整实现服务器的教程，而是一份演讲式技术材料。它主要回答三个问题：

1. 传统的“一条连接一个线程”为什么难以扩展？
2. Reactor 如何利用 IO 事件，把网络服务拆成许多非阻塞的小任务？
3. Java NIO 中的 `Buffer`、`Channel`、`Selector` 和 `SelectionKey` 如何共同实现 Reactor？

整份资料可以概括为下面这条链路：

```text
客户端连接
    |
    v
ServerSocketChannel -- OP_ACCEPT --> Acceptor
                                      |
                                      v
                              SocketChannel 注册到 Selector
                                      |
                                      v
Selector 发现 OP_READ/OP_WRITE
    |
    v
Handler 状态机：读取 -> 解码 -> 业务处理 -> 编码 -> 写回
```

---

## 一、网络服务到底在做什么

PDF 开头先把各种网络服务抽象成同一个流程：

```text
读取请求 -> 解码 -> 执行业务逻辑 -> 编码响应 -> 发送响应
```

例如，一个 HTTP 服务大致会经历：

```text
从 Socket 读取字节
    -> 解析 HTTP 请求行和请求头
    -> 查询数据或执行计算
    -> 生成 HTTP 响应
    -> 将响应字节写回 Socket
```

不同服务的区别，主要在于每一步的成本不同：

- XML 解析可能消耗较多 CPU；
- 文件传输可能主要消耗磁盘和网络带宽；
- 动态页面生成可能需要访问数据库；
- 计算型服务可能几乎不做 IO，而是长时间占用 CPU。

因此，不能只用“连接数量”判断系统压力，还要分析每个阶段的资源消耗。

---

## 二、传统服务器模型：一个连接一个处理线程

PDF 给出了传统 `ServerSocket` 循环的简化代码：

```java
class Server implements Runnable {
    public void run() {
        try {
            ServerSocket ss = new ServerSocket(PORT);

            while (!Thread.interrupted()) {
                // accept() 阻塞，直到有新的客户端连接
                Socket client = ss.accept();

                // 为这个连接创建一个处理线程
                new Thread(new Handler(client)).start();
            }
        } catch (IOException ex) {
            // 示例代码省略异常处理
        }
    }
}

static class Handler implements Runnable {
    private final Socket socket;

    Handler(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            byte[] input = new byte[MAX_INPUT];

            // read() 可能阻塞，直到收到数据
            socket.getInputStream().read(input);

            byte[] output = process(input);

            // write() 也可能阻塞
            socket.getOutputStream().write(output);
        } catch (IOException ex) {
            // 示例代码省略异常处理
        }
    }
}
```

这种模型的优点是直观：一个连接对应一个执行流程，代码可以按照“读、处理、写”的顺序编写。

但它有明显的扩展性问题：

### 1. 线程数量随着连接数量增长

如果同时存在一万个连接，最简单的实现可能就会创建一万个线程。线程本身需要栈空间、调度和上下文切换，很多连接其实处于空闲状态，却仍然占用线程资源。

### 2. 阻塞操作会占住线程

一个线程如果正在等待客户端发送下一段数据，它不能有效处理其他连接。线程数量不足时，新连接只能排队；线程数量过多时，调度成本又会升高。

### 3. 业务处理会阻塞接收流程

如果某个请求需要访问慢数据库或执行复杂计算，那么对应线程会长时间占用。对于长连接、聊天连接或大量空闲连接，这种资源分配方式尤其浪费。

PDF 这里并不是说“一条连接一个线程永远不能用”，而是说：当连接数很大、连接生命周期很长，或者连接大部分时间处于空闲状态时，需要更节约线程的模型。

---

## 三、可扩展性的目标：把处理拆成小任务

PDF 提出的核心思路是 **Divide and Conquer**，即“分而治之”。

把完整的请求处理拆成多个小阶段：

```text
读取 -> 解码 -> 计算 -> 编码 -> 发送
```

每个阶段尽量满足两个条件：

1. 做一件明确的事情；
2. 不要在执行期间长时间阻塞。

什么时候执行下一个阶段，由事件来触发。例如：

- Socket 可读：执行读取任务；
- 新连接到达：执行接收任务；
- Socket 可写：执行发送任务；
- 业务线程处理完毕：重新注册写事件。

这就是事件驱动模型：不是给每个客户端分配一个长期占用的线程，而是由少量事件循环线程不断观察“现在有哪些 IO 操作可以推进”。

### 事件驱动模型的优点

- 不需要为每个客户端创建一个线程；
- 减少线程切换；
- 减少线程栈和同步锁的开销；
- 连接数量很多但大部分处于空闲时，资源利用率更高。

### 事件驱动模型的代价

- 程序员必须自己保存每个连接的处理状态；
- 不能在 Reactor 线程中执行长时间阻塞的业务；
- 代码不再是简单的“读完再处理、处理完再写”；
- 必须正确处理半包、分段读取、部分写入和连接关闭。

PDF 用 AWT 图形界面事件作类比：用户点击按钮后，AWT 线程找到对应的 `ActionListener` 并调用它。网络 Reactor 也类似，只不过触发它的不是鼠标点击，而是 Socket 的 IO 就绪事件。

---

## 四、Reactor 模式是什么

Reactor 可以理解为一个“IO 事件分发器”：

1. Reactor 等待一组 Channel 的 IO 事件；
2. 操作系统告诉 Reactor 哪些 Channel 已经就绪；
3. Reactor 根据事件类型找到对应的 Handler；
4. Handler 执行一小段非阻塞处理；
5. 如果任务还没有完成，Handler 保存状态，等待下一次事件。

它的关键点不是“用了一个叫 Reactor 的类”，而是：

> 把 IO 等待集中管理，把具体动作绑定到事件上，并通过状态机分多次推进一个请求。

---

## 五、单线程 Reactor 的结构

PDF 的基础版本只有一个 Reactor 线程，结构可以表示为：

```text
                    +----------------+
新连接 ------------> | Acceptor       |
                    +----------------+
                            |
                            v
                    注册 SocketChannel
                            |
                            v
                    +----------------+
                    | Selector       |
                    | 事件循环        |
                    +----------------+
                      |            |
                OP_READ          OP_WRITE
                      |            |
                      v            v
                   Handler      Handler
```

单线程版本中，通常由同一个线程完成：

- 接收新连接；
- 读取网络数据；
- 解码请求；
- 执行业务逻辑；
- 编码响应；
- 写回客户端。

它结构简单，状态管理也相对容易。但如果业务计算很重，Reactor 线程就会被拖慢，所有连接的 IO 处理都会受到影响。因此，单线程 Reactor 更适合轻量业务或用于理解模型。

---

## 六、Reactor 第一步：创建 Selector 和监听 Channel

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

### 为什么要先设置非阻塞模式

`Selector` 的前提是 Channel 不应该因为一次读写而长时间阻塞。正确顺序通常是：

```java
channel.configureBlocking(false);
channel.register(selector, events);
```

阻塞 Channel 不能直接用于 Selector 注册。

---

## 七、Reactor 第二步：事件分发循环

PDF 中的事件循环是：

```java
public void run() {
    try {
        while (!Thread.interrupted()) {
            // 阻塞等待，直到至少有一个 IO 事件就绪
            selector.select();

            // 取出已经就绪的 SelectionKey
            Set<SelectionKey> selected = selector.selectedKeys();

            Iterator<SelectionKey> iterator = selected.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();

                // 处理完后立即从 selected 集合删除
                iterator.remove();

                dispatch(key);
            }
        }
    } catch (IOException ex) {
        // 生产代码需要记录日志并决定是否退出或重启
    }
}

private void dispatch(SelectionKey key) {
    if (!key.isValid()) {
        return;
    }

    Runnable handler = (Runnable) key.attachment();
    if (handler != null) {
        handler.run();
    }
}
```

### `select()` 做了什么

`selector.select()` 会让当前线程进入等待状态。操作系统发现某个注册的 Socket 可以进行对应操作时，Selector 返回，并把相关 `SelectionKey` 放到 `selectedKeys()` 中。

常见事件包括：

- `OP_ACCEPT`：监听 Socket 有新连接；
- `OP_READ`：连接有数据可读，或者读操作不会阻塞；
- `OP_WRITE`：连接可以继续写入；
- `OP_CONNECT`：非阻塞连接建立过程已经可以继续。

### 为什么不能直接遍历后只调用 `selected.clear()`

PDF 采用“遍历结束后清空”的简化写法。在实际代码中，更推荐在迭代时调用 `iterator.remove()`，因为这样可以明确表示某个事件已经消费，避免异常、提前跳出或重复处理时留下旧事件。

---

## 八、Acceptor：接收新连接

当监听 Channel 的 `OP_ACCEPT` 事件就绪时，Reactor 调用 `Acceptor`：

```java
class Acceptor implements Runnable {
    @Override
    public void run() {
        try {
            // 非阻塞 accept：没有连接时可能返回 null
            SocketChannel client = serverSocket.accept();

            if (client != null) {
                // Handler 会把客户端 Channel 注册到 Selector
                new Handler(selector, client);
            }
        } catch (IOException ex) {
            // 处理 accept 或初始化失败
        }
    }
}
```

`Acceptor` 的职责应该尽量简单：

1. 接受连接；
2. 配置非阻塞模式；
3. 创建连接对应的 Handler；
4. 把客户端 Channel 注册到 Selector。

不要在 `Acceptor` 中执行复杂业务，否则会延迟其他连接的事件处理。

---

## 九、Handler：用状态机处理一次请求

传统阻塞代码可以写成：

```text
read()
process()
write()
```

非阻塞 Reactor 不能假设一次 `read()` 就能读完整个请求，也不能假设一次 `write()` 就能写完全部响应。因此 Handler 必须保存状态。

PDF 使用两个基本状态：

```java
private static final int READING = 0;
private static final int SENDING = 1;

private int state = READING;
```

### Handler 初始化

```java
final class Handler implements Runnable {
    private final SocketChannel socket;
    private final SelectionKey key;

    // 输入和输出缓冲区
    private final ByteBuffer input = ByteBuffer.allocate(MAX_IN);
    private final ByteBuffer output = ByteBuffer.allocate(MAX_OUT);

    private static final int READING = 0;
    private static final int SENDING = 1;
    private int state = READING;

    Handler(Selector selector, SocketChannel socket) throws IOException {
        this.socket = socket;

        // 客户端连接也必须设置为非阻塞
        socket.configureBlocking(false);

        // 先注册 0，后面再设置真正关注的事件
        key = socket.register(selector, 0);
        key.attach(this);

        // 初始阶段只关心可读事件
        key.interestOps(SelectionKey.OP_READ);

        // 如果注册动作发生在另一个线程，需要唤醒 Selector
        selector.wakeup();
    }
}
```

### 读取阶段

```java
private void read() throws IOException {
    int count = socket.read(input);

    if (count == -1) {
        // 对端已经关闭连接
        key.cancel();
        socket.close();
        return;
    }

    if (inputIsComplete()) {
        // 只有确定一条完整请求到达后，才能开始业务处理
        process();

        state = SENDING;

        // 接下来等待 Socket 可写
        key.interestOps(SelectionKey.OP_WRITE);
    }
}
```

`inputIsComplete()` 不是简单地判断“本次读取是否大于 0”。它应该根据协议判断请求是否完整，例如：

- 定长协议：缓冲区达到指定长度；
- 分隔符协议：读到了 `\\r\\n\\r\\n`；
- 长度字段协议：已经读取到完整消息体；
- HTTP：请求头和 `Content-Length` 指定的请求体都已完整。

### 发送阶段

```java
private void send() throws IOException {
    int count = socket.write(output);

    // 一次 write 可能只写出一部分
    if (outputIsComplete()) {
        // 当前请求完成，可以取消这个 SelectionKey
        key.cancel();
        socket.close();
    }
}
```

实际使用时，必须保证 `output` 已经切换到读模式：

```java
output.flip();
```

如果响应还没有写完，就继续保留 `OP_WRITE`；只有输出缓冲区为空时，才取消 `OP_WRITE`。如果无论有没有数据都一直监听 `OP_WRITE`，Selector 可能持续返回，形成高 CPU 空转。

### Handler 的统一入口

```java
@Override
public void run() {
    try {
        if (state == READING) {
            read();
        } else if (state == SENDING) {
            send();
        }
    } catch (IOException ex) {
        key.cancel();
        try {
            socket.close();
        } catch (IOException ignored) {
            // 关闭失败通常只记录日志
        }
    }
}
```

这就是 Reactor 的关键：一次事件只推进状态机的一小步，未完成的工作留到下一次事件继续执行。

---

## 十、使用不同 Handler 表示不同状态

PDF 还介绍了 State-Object 的变体：读取阶段把 `Reader` 作为附件，读取完成后把附件替换成 `Sender`。

```java
class Reader implements Runnable {
    @Override
    public void run() {
        socket.read(input);

        if (inputIsComplete()) {
            process();

            // 状态转移：Reader -> Sender
            key.attach(new Sender());
            key.interestOps(SelectionKey.OP_WRITE);
            key.selector().wakeup();
        }
    }
}

class Sender implements Runnable {
    @Override
    public void run() {
        socket.write(output);

        if (outputIsComplete()) {
            key.cancel();
        }
    }
}
```

这种写法的优点是每个状态的代码边界更清楚；缺点是状态对象之间共享数据、异常处理和资源关闭会更复杂。现代 Netty 中的 `ChannelPipeline` 和多个 Handler，也可以看成这种“事件和处理器绑定”思想的工程化实现。

---

## 十一、多线程 Reactor：把业务处理移出 IO 线程

单线程 Reactor 最大的问题是：业务处理不能太慢。

如果 Reactor 线程正在执行：

```java
process(); // 慢数据库、复杂计算、远程调用
```

那么其他连接的读写事件都不能及时处理。PDF 因此介绍了两种扩展方向。

### 方案一：Reactor + Worker 线程池

基本结构：

```text
Reactor 线程：accept / read / write
       |
       | 请求读完整
       v
Worker 线程池：decode / compute / encode
       |
       | 业务处理完成
       v
Reactor 线程：继续 write
```

核心原则：

- Reactor 线程只负责快速的 IO 操作和任务分发；
- 计算密集型或可能阻塞的业务交给 Worker；
- Worker 完成后，通知 Reactor 重新关注 `OP_WRITE`；
- Worker 数量通常远小于客户端连接数量。

PDF 中的示意代码使用了旧版 `PooledExecutor`：

```java
private void read() throws IOException {
    socket.read(input);

    if (inputIsComplete()) {
        state = PROCESSING;

        // 不在 Reactor 线程中直接执行耗时业务
        pool.execute(new Processor());
    }
}

class Processor implements Runnable {
    @Override
    public void run() {
        processAndHandOff();
    }
}
```

今天更常见的是 `ExecutorService`：

```java
ExecutorService workers = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors()
);

workers.execute(() -> {
    // 这里执行解码、业务计算、编码
    process();

    // 注意：跨线程修改 SelectionKey 后，通常需要唤醒 Selector
    selector.wakeup();
});
```

### Worker 线程池的风险

如果请求处理速度小于到达速度，任务队列会不断增长。因此线程池必须配合：

- 有界队列；
- 拒绝策略或降级策略；
- 超时控制；
- 连接级背压；
- 监控队列长度和任务等待时间。

否则，线程池只是把阻塞从 IO 线程转移到了内存队列，最终仍可能发生延迟堆积和内存耗尽。

### 方案二：多个 Reactor

当 IO 事件本身也很多时，可以使用多个 Reactor：

```text
                    Main Reactor
                         |
              负责 OP_ACCEPT 接收连接
                 /       |       \
                v        v        v
          Sub Reactor  Sub Reactor  Sub Reactor
          Selector     Selector     Selector
             |             |             |
          客户端 IO     客户端 IO     客户端 IO
```

通常分为：

- Main Reactor：只负责接收连接；
- Sub Reactor：负责已建立连接的读写事件；
- Worker Pool：负责业务计算。

PDF 给出的分配方式是轮询：

```java
private int next = 0;

void distribute(SocketChannel client) {
    Reactor reactor = reactors[next];

    // 把连接交给下一个 Sub Reactor
    reactor.register(client);

    next = (next + 1) % reactors.length;
}
```

轮询简单，但不一定代表真实负载均衡。实际系统还要考虑连接活跃度、请求量、消息量和 Reactor 当前队列长度。

---

## 十二、任务之间如何协调

PDF 列出四类协调方式：

### 1. 直接交接

一个任务直接调用下一个任务。速度快，但模块耦合比较强，错误处理也容易变复杂。

### 2. 回调

任务完成后调用回调对象，由回调对象改变状态、替换附件或注册下一个事件。

### 3. 队列

前一个阶段把数据或任务放入队列，后一个阶段从队列取出。队列可以吸收短时间流量波动，但要注意队列长度和内存上限。

### 4. Future

一个任务生成结果，后续任务等待结果。它适合异步计算，但如果大量调用方直接阻塞等待 Future，就会重新引入阻塞问题。

---

## 十三、Java NIO 的四个核心对象

PDF 后半部分转成了 Java NIO API 速查表。可以用下面的关系理解：

```text
Channel 负责搬运数据
Buffer   负责保存数据
Selector 负责等待哪些 Channel 已经就绪
SelectionKey 负责保存注册关系、事件和 Handler
```

### 1. Buffer

`Buffer` 是一块带有位置状态的内存区域，核心属性是：

- `capacity`：容量，创建后通常固定；
- `position`：当前读写位置；
- `limit`：当前可读或可写边界；
- `mark`：可选的标记位置。

最重要的操作是：

```java
ByteBuffer buffer = ByteBuffer.allocate(1024);

// 写入数据到 Buffer
buffer.put(bytes);

// 从“写入模式”切换为“读取模式”
buffer.flip();

// Channel 从 Buffer 中读取数据
channel.write(buffer);

// 读取完成后，准备复用整个 Buffer
buffer.clear();
```

状态转换可以记成：

```text
clear() -> put()/Channel.read() -> flip() -> get()/Channel.write() -> clear()
```

注意：

- `clear()` 不会擦除字节，只是把 `position` 设为 0、`limit` 设为 `capacity`；
- `flip()` 也不会复制数据，它把 `limit` 设为当前 `position`，再把 `position` 设为 0；
- 如果忘记 `flip()`，写入端可能从错误的位置读取；
- 如果数据没有读完就调用 `clear()`，会丢失未处理数据；
- `compact()` 适合保留尚未处理的半包数据，并把剩余数据移到缓冲区开头。

### 2. ByteBuffer

`ByteBuffer` 是网络编程最常用的 Buffer。PDF 列出的能力包括：

- `allocate(capacity)`：创建普通堆内存 Buffer；
- `allocateDirect(capacity)`：创建直接内存 Buffer；
- `wrap(byte[])`：用已有字节数组包装 Buffer；
- `get` / `put`：读写字节；
- `slice` / `duplicate`：创建共享数据的视图；
- `compact`：保留未读数据；
- `order`：设置字节序。

直接内存并不意味着任何场景都更快。它减少了某些 IO 路径中的复制，但创建和回收成本较高，通常适合长生命周期、频繁 IO 的场景，不能简单地把所有 Buffer 都改成 direct Buffer。

### 3. Channel

Channel 是数据的输入输出通道。与传统流相比，它通常与 Buffer 配合使用：

```java
int n = channel.read(buffer);  // 把 Channel 数据读入 Buffer
int m = channel.write(buffer); // 把 Buffer 数据写入 Channel
```

常见 Channel 包括：

- `SocketChannel`：客户端 TCP 连接；
- `ServerSocketChannel`：服务端 TCP 监听；
- `FileChannel`：文件读写；
- `SelectableChannel`：可以注册到 Selector 的 Channel 基类。

`read()` 和 `write()` 的返回值很重要：

- 大于 0：实际读写的字节数；
- 等于 0：当前不能继续读写，非阻塞模式下很常见；
- 读取返回 `-1`：对端已经正常关闭输入端。

不能假设一次 `read()` 读完整条消息，也不能假设一次 `write()` 写完全部响应。

### 4. Selector

`Selector` 负责集中等待多个 Channel 的就绪事件：

```java
Selector selector = Selector.open();

channel.configureBlocking(false);
SelectionKey key = channel.register(
        selector,
        SelectionKey.OP_READ
);

while (true) {
    selector.select();

    for (SelectionKey selectedKey : selector.selectedKeys()) {
        // 根据事件处理 selectedKey
    }
}
```

Selector 本身不读取数据，也不执行业务逻辑；它只负责告诉程序“哪些 Channel 现在可以推进”。

### 5. SelectionKey

`SelectionKey` 是 Channel、Selector、事件集合和附件之间的联系：

```java
SelectionKey key = channel.register(
        selector,
        SelectionKey.OP_READ
);

key.attach(handler);

if (key.isReadable()) {
    // 执行读处理
}

if (key.isWritable()) {
    // 执行写处理
}
```

其中：

- `interestOps()`：程序希望监听的事件；
- `readyOps()`：当前实际已经就绪的事件；
- `attachment()`：绑定的 Handler 或状态对象；
- `cancel()`：取消注册关系；
- `wakeup()`：唤醒正在 `select()` 中等待的线程。

`interestOps` 和 `readyOps` 不要混淆：前者是“我想关注什么”，后者是“现在发生了什么”。

---

## 十四、文件传输和零拷贝提示

PDF 还提到了 `FileChannel`、内存映射文件和 direct buffer。

文件传输时，数据路径可能是：

```text
磁盘 -> 用户态 Buffer -> Socket -> 网络
```

这条路径可能产生多次复制。`FileChannel.transferTo()`、`transferFrom()` 和内存映射文件有机会减少用户态复制，某些操作系统还可以使用内核级的零拷贝机制。

但“零拷贝”不是绝对不会发生任何复制，而是减少特定路径中的数据复制。它是否有收益，取决于：

- 文件大小；
- 连接持续时间；
- 操作系统和文件系统；
- 网络设备；
- 是否需要修改数据；
- 创建和维护直接内存的成本。

---

## 十五、长连接服务需要保存会话状态

对于数据库连接、事务处理、多人游戏和聊天系统，客户端通常不是“连接、发送一次请求、立即断开”，而是：

```text
建立连接
    -> 连续发送多条消息
    -> 服务端持续返回结果
    -> 最终断开
```

这类服务需要为每个连接保存：

- 当前协议解析到哪里；
- 是否有半包；
- 当前会话和用户身份；
- 待发送响应；
- 心跳和超时信息；
- 连接是否正在关闭。

这也是 Reactor 中 Handler 必须是状态机的原因。连接数量一多，状态保存和生命周期管理往往比 `Selector` 本身更难。

---

## 十六、PDF 中代码的时代背景和需要修正的地方

这份材料非常经典，但代码明显带有早期 Java NIO 的时代特征。阅读时应注意：

### 1. 缺少泛型

原文使用了类似：

```java
Set selected = selector.selectedKeys();
Iterator it = selected.iterator();
```

现代 Java 应写成：

```java
Set<SelectionKey> selected = selector.selectedKeys();
Iterator<SelectionKey> iterator = selected.iterator();
```

### 2. `PooledExecutor` 已是旧式示例

PDF 中的 `util.concurrent` / `PooledExecutor` 是早期并发库的写法。现在通常使用：

```java
ExecutorService pool = Executors.newFixedThreadPool(8);
```

生产代码还应使用有界队列、拒绝策略和关闭流程。

### 3. 代码中的若干写法是演讲简化或排版问题

例如原文中的 `dispatch((SelectionKey)(it.next());` 少了括号，正确意图应是：

```java
dispatch((SelectionKey) iterator.next());
```

原文状态对象示例中出现的 `sk.interest(...)`，按 Java NIO API 的实际方法应理解为：

```java
sk.interestOps(SelectionKey.OP_WRITE);
```

这些代码应当用来理解结构，不宜直接复制运行。

### 4. 示例省略了最重要的异常和边界处理

完整实现还需要处理：

- 客户端异常断开；
- `read()` 返回 `-1`；
- `read()` / `write()` 返回 0；
- 半包和粘包；
- 输出缓冲区写不完；
- `CancelledKeyException`；
- Selector 线程和业务线程之间的并发注册；
- 空闲连接超时；
- 大消息限制和内存上限。

---

## 十七、最小可运行思维模型

不考虑协议细节时，可以把 Reactor 记成下面的伪代码：

```java
while (serverIsRunning) {
    selector.select();

    for (SelectionKey key : readyKeys) {
        removeFromSelectedKeys(key);

        if (key.isAcceptable()) {
            acceptNewConnection();
        }

        if (key.isReadable()) {
            readAndAccumulateBytes();
        }

        if (requestIsComplete()) {
            dispatchBusinessTaskToWorkerPool();
        }

        if (key.isWritable()) {
            writeAsMuchAsPossible();
        }
    }
}
```

真正实现时，读取、业务处理和发送不能简单地在同一轮全部完成，而是根据连接状态逐步推进：

```text
READING
  |
  | 请求完整
  v
PROCESSING
  |
  | 业务处理完成
  v
SENDING
  |
  | 响应写完
  v
READING 或 CLOSED
```

---

## 十八、如何把这份 PDF 和 Netty 联系起来

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

## 十九、学习时最应该记住的结论

### 结论一：Reactor 不是“多线程的别名”

Reactor 的本质是 IO 事件分发和状态机。它可以是单线程，也可以结合 Worker 线程池和多个 Reactor 线程。

### 结论二：非阻塞不等于不需要线程

非阻塞 IO 只解决了“线程不要卡在 IO 等待上”。业务计算、锁竞争、GC、磁盘访问和远程调用仍可能阻塞或消耗大量时间。

### 结论三：一个事件通常只代表“可以继续推进”

`OP_READ` 不代表完整请求已经到达，`OP_WRITE` 也不代表所有数据都能一次写完。程序必须检查实际读写字节数。

### 结论四：状态管理是 Reactor 的难点

每条连接都可能处于读取、解析、业务处理、发送、关闭等状态。状态没有保存好，就会出现半包丢失、重复处理、响应错配和连接泄漏。

### 结论五：慢任务必须隔离

Reactor/EventLoop 线程应快速返回。数据库查询、文件操作、远程 RPC、复杂计算等慢任务，应交给合适的线程池或异步组件。

### 结论六：Selector 只是基础设施

高性能网络服务最终还需要协议设计、背压、超时、限流、内存管理、线程模型、监控和故障恢复。只会调用 `selector.select()`，还不能构成一个可靠的高并发服务器。

---

## 二十、推荐实践顺序

可以按下面的顺序把 PDF 内容变成代码能力：

1. 用阻塞 `ServerSocket` 写一个最简单的回显服务器；
2. 改成 `ServerSocketChannel` 和 `SocketChannel`；
3. 使用一个 Selector 同时处理多个客户端；
4. 实现长度字段协议，观察半包和粘包；
5. 实现部分写入，正确管理 `OP_WRITE`；
6. 把业务处理移入有界线程池；
7. 增加连接超时、最大消息长度和异常关闭；
8. 再开始阅读 Netty 的 EventLoop 和 Pipeline 源码。

如果只记一条：

> Reactor 用少量线程管理大量连接；Selector 负责发现 IO 就绪，Handler 负责推进连接状态，Worker 负责隔离慢业务。

