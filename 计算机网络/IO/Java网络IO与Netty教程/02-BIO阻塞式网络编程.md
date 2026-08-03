# 二、BIO 阻塞式网络编程

## 2.1 BIO 的控制流

```text
主线程 --accept() 阻塞--> 新连接
                          |
                          v
                    工作线程 --read() 阻塞--> 请求
```

最初级写法在主线程处理连接，一个慢客户端会挡住后续连接；常见改进是把已连接 Socket 交给有界线程池。

## 2.2 教程代码：有界线程池 Echo Server

完整代码：`示例代码/src/main/java/note/io/bio/BioEchoServer.java`。

```java
ExecutorService workers = new ThreadPoolExecutor(
        4, 16, 60, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(100),
        new ThreadPoolExecutor.AbortPolicy());

try (ServerSocket server = new ServerSocket(9000)) {
    while (!workers.isShutdown()) {
        Socket client = server.accept();
        try {
            workers.execute(() -> handle(client));
        } catch (RejectedExecutionException rejected) {
            client.close(); // 过载时明确拒绝，避免无限堆积
        }
    }
}
```

处理按换行分隔的请求：

```java
private static void handle(Socket socket) {
    try (socket;
         var reader = new BufferedReader(new InputStreamReader(
                 socket.getInputStream(), StandardCharsets.UTF_8));
         var writer = new BufferedWriter(new OutputStreamWriter(
                 socket.getOutputStream(), StandardCharsets.UTF_8))) {

        socket.setSoTimeout(30_000);
        String line;
        while ((line = reader.readLine()) != null) {
            writer.write("echo: " + line);
            writer.newLine();
            writer.flush();
        }
    } catch (SocketTimeoutException timeout) {
        System.out.println("客户端长时间无数据：" + socket);
    } catch (IOException e) {
        System.out.println("连接结束：" + e.getMessage());
    }
}
```

这里用换行符定义了应用层消息边界。`readLine` 简化了教学代码，但生产环境仍要限制单行最大长度，防止恶意客户端无限发送而不换行。

## 2.3 为什么线程池不是把 BIO 变成 NIO

线程池限制了线程数量和任务队列，但工作线程仍阻塞在 `readLine`。当 16 个客户端都保持连接且不发数据时，其他请求只能排队。它解决“无限创建线程”，没有解决“空闲连接占用工作线程”。

## 2.4 超时、半关闭和优雅停机

- `setSoTimeout` 限制单次阻塞读取等待时间，不是整个请求总时长。
- 对端 `shutdownOutput` 后，本端读取会到 EOF，但本端仍可继续写，称为 TCP 半关闭。
- 停机时应先停止接收，再等待在途任务，最后强制结束超时任务。
- `try-with-resources` 应覆盖 Socket 和包装流，避免异常路径泄漏。

## 2.5 官方 API

- [ServerSocket](https://www.apiref.com/java11-zh/java.base/java/net/ServerSocket.html)
- [Socket](https://www.apiref.com/java11-zh/java.base/java/net/Socket.html)
- [BufferedReader.readLine](https://www.apiref.com/java11-zh/java.base/java/io/BufferedReader.html#readLine())
- [ThreadPoolExecutor](https://www.apiref.com/java11-zh/java.base/java/util/concurrent/ThreadPoolExecutor.html)
- [SocketTimeoutException](https://www.apiref.com/java11-zh/java.base/java/net/SocketTimeoutException.html)

## 2.6 知识问答

**问：一个连接一个虚拟线程是否让 NIO/Netty 失去价值？**

答：虚拟线程显著降低了“阻塞风格代码对应大量线程”的成本，适合许多请求/响应服务；但它没有自动提供协议编解码、连接生命周期、背压、缓冲区池、统一 Pipeline 和多 transport。选型要比较整体编程模型，而不是只比线程数量。

**问：为什么线程池队列必须有界？**

答：无界队列会把过载转化为内存增长和极高延迟。有界队列配合拒绝策略能让系统在容量外快速失败。

**问：客户端断网后 `read` 会立刻返回吗？**

答：不一定。拔网线与正常发送 FIN 不同，连接可能直到 TCP 重传超时、keepalive 或应用心跳超时才被发现。

### 动手题

启动 20 个“连接后不发送”的客户端，观察线程池耗尽；设置读取超时后再次观察。这解释了为什么长连接场景更偏爱事件驱动模型。

------

上一章：[[01-网络IO基本功与常见模型]]　下一章：[[03-NIO的Buffer与Channel]]
