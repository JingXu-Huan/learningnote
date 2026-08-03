# 三、NIO 的 Buffer 与 Channel

## 3.1 NIO 的三个基本对象

```text
Channel：数据通道
Buffer：数据容器与游标状态
Selector：多个可选择 Channel 的就绪事件集合
```

本章先掌握前两个。网络读取是 `Channel -> Buffer`，网络写出是 `Buffer -> Channel`。

## 3.2 ByteBuffer 的状态机

`ByteBuffer` 关键状态满足：

```text
0 <= mark <= position <= limit <= capacity
```

- 写模式：`position` 指向下一个写入位置，`limit` 通常等于容量。
- `flip()`：令 `limit = position`、`position = 0`，切到读模式。
- `clear()`：准备覆盖整个缓冲区，不会擦除内存。
- `compact()`：保留未读字节并移到前部，适合保留半包。

## 3.3 教程代码：观察游标

```java
ByteBuffer buffer = ByteBuffer.allocate(8);
print(buffer);            // pos=0, limit=8, cap=8

buffer.put((byte) 'A');
buffer.put((byte) 'B');
print(buffer);            // pos=2, limit=8, cap=8

buffer.flip();
print(buffer);            // pos=0, limit=2, cap=8

System.out.println((char) buffer.get());
buffer.compact();
print(buffer);            // 未读的 B 被移到开头，pos=1
```

```java
private static void print(ByteBuffer b) {
    System.out.printf("pos=%d, limit=%d, cap=%d%n",
            b.position(), b.limit(), b.capacity());
}
```

## 3.4 正确处理读取和部分写

```java
int n = channel.read(buffer);
if (n == -1) {
    channel.close();
    return;
}

buffer.flip();
while (buffer.hasRemaining()) {
    int written = channel.write(buffer);
    if (written == 0) {
        // 注册 OP_WRITE 后返回事件循环，不能忙等
        break;
    }
}
buffer.compact();
```

## 3.5 Heap、Direct 与零拷贝

- `allocate` 创建堆缓冲区，创建便宜，能直接访问数组。
- `allocateDirect` 创建直接缓冲区，某些本地 IO 路径可少一次复制，但分配/回收更贵。
- `FileChannel.transferTo` 可利用操作系统能力减少文件发送过程中的用户态复制。
- “零拷贝”不是绝对零次复制，而是减少 CPU 参与的数据搬运和上下文切换。

不要在热路径反复创建大 DirectBuffer；Netty 使用池化分配器解决的正是这类成本。

## 3.6 官方 API

- [ByteBuffer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html)
- [Buffer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/Buffer.html)
- [SocketChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html)
- [FileChannel.transferTo](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html#transferTo(long,long,java.nio.channels.WritableByteChannel))

## 3.7 知识问答

**问：`clear()` 会把数据清零吗？**

答：不会，只重置游标。旧字节仍在内存中，但后续写入可覆盖它们。

**问：为什么一次 `write` 可能写不完？**

答：Socket 发送缓冲区容量有限；非阻塞模式下只写当前能接收的部分，剩余数据要排队并等待 `OP_WRITE`。

**问：`flip` 与 `rewind` 的区别？**

答：两者都把 `position` 设为 0；`flip` 还把 `limit` 设为原 `position`，用于写转读；`rewind` 保留 `limit`，用于重新读取同一段内容。

**问：`slice` 后的数据是复制的吗？**

答：不是。切片与原缓冲区共享底层内容，但有独立游标，修改内容会互相可见。

### 动手题

给长度字段协议准备一个 8 字节 Buffer，第一次只放入 2 字节长度头，第二次再放剩余头和正文。用 `compact` 保留第一次的半包。

------

上一章：[[02-BIO阻塞式网络编程]]　下一章：[[04-Selector与手写Reactor]]

