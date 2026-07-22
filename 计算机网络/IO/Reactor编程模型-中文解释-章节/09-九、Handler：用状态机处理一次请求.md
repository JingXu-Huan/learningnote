# 九、Handler：用状态机处理一次请求

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

## Handler 初始化

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

## 读取阶段

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

## 发送阶段

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

## Handler 的统一入口

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

