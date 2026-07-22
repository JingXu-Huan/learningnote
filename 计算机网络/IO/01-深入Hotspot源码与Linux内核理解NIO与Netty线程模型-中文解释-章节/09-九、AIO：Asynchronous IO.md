# 九、AIO：Asynchronous IO

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

## 9.1 AIO 服务端的规范排版

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

## 9.2 AIO 客户端的规范排版

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

