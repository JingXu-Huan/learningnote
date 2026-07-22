# 十三、Java NIO 的四个核心对象

PDF 后半部分转成了 Java NIO API 速查表。可以用下面的关系理解：

```text
Channel 负责搬运数据
Buffer   负责保存数据
Selector 负责等待哪些 Channel 已经就绪
SelectionKey 负责保存注册关系、事件和 Handler
```

## 1. Buffer

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

## 2. ByteBuffer

`ByteBuffer` 是网络编程最常用的 Buffer。PDF 列出的能力包括：

- `allocate(capacity)`：创建普通堆内存 Buffer；
- `allocateDirect(capacity)`：创建直接内存 Buffer；
- `wrap(byte[])`：用已有字节数组包装 Buffer；
- `get` / `put`：读写字节；
- `slice` / `duplicate`：创建共享数据的视图；
- `compact`：保留未读数据；
- `order`：设置字节序。

直接内存并不意味着任何场景都更快。它减少了某些 IO 路径中的复制，但创建和回收成本较高，通常适合长生命周期、频繁 IO 的场景，不能简单地把所有 Buffer 都改成 direct Buffer。

## 3. Channel

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

## 4. Selector

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

## 5. SelectionKey

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

