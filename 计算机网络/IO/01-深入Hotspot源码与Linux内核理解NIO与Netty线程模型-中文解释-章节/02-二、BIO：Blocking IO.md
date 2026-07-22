# 二、BIO：Blocking IO

BIO 是同步阻塞 IO。最直观的模型是：

```text
客户端 1 -> 服务端线程 1
客户端 2 -> 服务端线程 2
客户端 3 -> 服务端线程 3
```

如果一个客户端长时间不发送数据，对应线程就会阻塞在 `read()` 上。这个线程无法有效处理其他客户端。

## 2.1 PDF 中 BIO 服务端代码的含义

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

## 2.2 为每个客户端创建线程

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

## 2.3 BIO 客户端

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

## 2.4 BIO 的优缺点

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

