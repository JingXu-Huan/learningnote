# 四、Selector 与手写 Reactor

## 4.1 从轮询连接到轮询就绪事件

如果逐个调用非阻塞 Channel 的 `read`，大量空连接会产生无效轮询。`Selector` 把“哪些 Channel 就绪”交给底层多路复用机制集中等待。

```text
注册：Channel + interestOps + attachment -> SelectionKey
等待：selector.select()
分发：遍历 selectedKeys
处理：ACCEPT / CONNECT / READ / WRITE
```

这里最容易混淆的是 `SelectionKey`。你可以把它理解成“Channel 注册到 Selector 之后生成的一张工单”：

| 字段 | 作用 |
| --- | --- |
| `channel()` | 对应哪一个 Channel |
| `selector()` | 被注册到了哪一个 Selector |
| `interestOps()` | 我希望关注哪些事件，例如 `OP_READ`、`OP_WRITE` |
| `readyOps()` | 底层已经准备好的事件 |
| `attachment()` | 这条连接自己的上下文数据 |

也就是说，`SelectionKey` 不是“连接本身”，而是“连接 + 感兴趣事件 + 就绪事件 + 业务上下文”的组合入口。Reactor 线程拿到 Key 以后，通常不是去全局查表，而是直接通过 Key 找到对应连接状态。

## 4.2 教程代码：最小 Reactor

完整代码：`示例代码/src/main/java/note/io/nio/NioEchoServer.java`。

```java
try (Selector selector = Selector.open();
     ServerSocketChannel server = ServerSocketChannel.open()) {
    server.configureBlocking(false);
    server.bind(new InetSocketAddress(9001));
    server.register(selector, SelectionKey.OP_ACCEPT);

    while (true) {
        selector.select();
        Iterator<SelectionKey> iterator =
                selector.selectedKeys().iterator();

        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove(); // selected 集合不会自动清空

            try {
                if (key.isAcceptable()) {
                    accept(server, selector);
                }
                if (key.isReadable()) {
                    read(key);
                }
                if (key.isValid() && key.isWritable()) {
                    write(key);
                }
            } catch (IOException e) {
                key.cancel();
                key.channel().close();
            }
        }
    }
}
```

连接注册与附件：

```java
private static void accept(ServerSocketChannel server, Selector selector)
        throws IOException {
    SocketChannel client;
    while ((client = server.accept()) != null) {
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ,
                new ConnectionState(ByteBuffer.allocate(1024)));
    }
}
```

`attachment` 保存每条连接自己的读缓冲区、待写队列和协议解析状态，绝不能让所有连接共享一个可变 ByteBuffer。

## 4.3 OP_WRITE 为什么不能一直监听，以及这些位运算在干什么

Socket 大多数时间都可写。永久关注 `OP_WRITE` 会让 `select` 频繁立即返回，形成空转：

```java
// 有待写数据时打开
key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

// 队列清空后关闭
key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
```

这里的写法看起来像“魔法”，其实只是位图开关：

| 表达式 | 含义 |
| --- | --- |
| `a | b` | 把某一位打开，适合“增加关注事件” |
| `a & b` | 只保留双方都为 1 的位，适合“按掩码删除事件” |
| `~b` | 按位取反，把目标位变成 0，其余位变成 1 |

所以：

- `key.interestOps() | SelectionKey.OP_WRITE` 的意思是“在原有关注事件上，再加上 WRITE”；
- `key.interestOps() & ~SelectionKey.OP_WRITE` 的意思是“保留原有事件，但把 WRITE 那一位清掉”。

如果直接写成 `key.interestOps(SelectionKey.OP_WRITE)`，就会把原本的 `OP_READ`、`OP_ACCEPT` 等关注事件覆盖掉，导致连接行为异常。这里一定要先理解：`interestOps` 本质上是一个事件位图，而不是一个单独枚举值。

## 4.4 跨线程修改 interestOps

业务线程把响应放入连接队列后，应调用 `selector.wakeup()`，使阻塞在 `select` 的 Reactor 尽快看到变化。更稳妥的设计是业务线程只提交任务，由 Reactor 线程统一修改 Key 和写队列。

## 4.5 Netty 对应关系

| 手写 NIO | Netty |
| --- | --- |
| `Selector` + 循环线程 | `NioEventLoop` |
| `SocketChannel` | `NioSocketChannel` |
| `SelectionKey.attachment` | `ChannelPipeline` 与 Channel 状态 |
| if/else 分发事件 | Pipeline 传播事件 |
| 自己管理待写队列 | `ChannelOutboundBuffer` |

Netty 不是另一套网络原理，而是把这些易错细节封装成稳定抽象。

## 4.6 官方 API

- [Selector](https://www.apiref.com/java11-zh/java.base/java/nio/channels/Selector.html)
- [SelectionKey](https://www.apiref.com/java11-zh/java.base/java/nio/channels/SelectionKey.html)
- [SelectableChannel.register](https://www.apiref.com/java11-zh/java.base/java/nio/channels/SelectableChannel.html#register(java.nio.channels.Selector,int,java.lang.Object))
- [Selector.wakeup](https://www.apiref.com/java11-zh/java.base/java/nio/channels/Selector.html#wakeup())
- [Netty NioEventLoop](https://netty.io/4.1/api/io/netty/channel/nio/NioEventLoop.html)

## 4.7 知识问答

**问：`selectedKeys` 为什么必须 remove？**

答：它是 Selector 维护的已选择集合，不会因遍历自动移除。不删除会重复处理旧 Key。

**问：`isReadable` 是否保证 `read` 一定大于 0？**

答：不保证。就绪状态可能变化，处理代码必须接受 `0`；读取 `-1` 则关闭连接。

**问：一个 Selector 能否被多个线程同时 select？**

答：API 允许线程安全的特定操作，但典型 Reactor 让一个线程拥有事件循环，降低状态同步复杂度；横向扩展通常使用多个 EventLoop/Selector。

**问：Reactor 线程能直接查数据库吗？**

答：不应执行不可控的阻塞调用。它一旦阻塞，所属 Selector 上的其他连接都无法推进。

### 动手题

故意不删除 selected Key、永久注册 `OP_WRITE`，分别观察重复日志和 CPU。然后恢复正确写法。

------

上一章：[[03-NIO的Buffer与Channel]]　下一章：[[05-AIO异步完成模型]]
