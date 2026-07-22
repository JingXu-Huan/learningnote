# 十三、原 PDF 示例代码需要注意的坑

## 1. 一次 `read()` 不等于一条完整消息

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

## 2. 只能处理有效字节

不要直接这样写：

```java
new String(buffer.array())
```

应该使用实际读取长度：

```java
new String(buffer.array(), 0, read, StandardCharsets.UTF_8)
```

或者使用 `flip()` 和 `buffer.get()`。

## 3. `read() == 0` 不是连接关闭

在非阻塞模式下：

- `read() > 0`：读到了数据；
- `read() == 0`：现在没有可读数据；
- `read() == -1`：对端关闭连接。

## 4. `write()` 也可能只写一部分

如果响应没有一次写完，要保留剩余数据，并继续关注 `OP_WRITE`。没有待发送数据时，不要一直注册 `OP_WRITE`，否则容易导致事件循环空转。

## 5. Buffer 不能随意清空

半包数据必须保存。处理完已经消费的部分后，可以用 `compact()` 把未处理数据移到开头，再继续读取。

## 6. `Selector` 不是业务线程池

Selector 线程应该快速完成 accept、read、write 和状态切换。数据库查询、文件 IO、远程调用和复杂计算应交给业务线程池，否则一个慢任务会拖住所有连接。

## 7. `selector.select()` 不是“异步执行”

NIO 的 `select()` 仍然由当前线程主动等待和处理事件，所以它通常归类为同步非阻塞 IO。AIO 才是提交操作后由完成通知驱动后续逻辑。

## 8. Windows 不能直接套用 Linux epoll 细节

Java NIO 会根据平台选择底层实现。Linux 常见的是 epoll，Windows 使用 Winsock 相关机制。应用程序应使用 Java NIO API，而不是把 Linux 的 epoll fd、系统调用和数据结构直接写进跨平台 Java 代码。

---

