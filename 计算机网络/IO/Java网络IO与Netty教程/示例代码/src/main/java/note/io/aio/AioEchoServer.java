package note.io.aio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CountDownLatch;

public final class AioEchoServer {
    private static final int PORT = 9002;

    private AioEchoServer() {
    }

    public static void main(String[] args) throws Exception {
        AsynchronousServerSocketChannel server =
                AsynchronousServerSocketChannel.open()
                        .bind(new InetSocketAddress(PORT));
        System.out.println("AIO Echo listening on " + PORT);
        acceptAgain(server);
        new CountDownLatch(1).await();
    }

    private static void acceptAgain(
            AsynchronousServerSocketChannel server) {
        server.accept(null, new CompletionHandler<>() {
            @Override
            public void completed(
                    AsynchronousSocketChannel client, Object attachment) {
                acceptAgain(server);
                readAgain(client, ByteBuffer.allocate(1024));
            }

            @Override
            public void failed(Throwable error, Object attachment) {
                if (server.isOpen()) {
                    error.printStackTrace();
                    acceptAgain(server);
                }
            }
        });
    }

    private static void readAgain(
            AsynchronousSocketChannel client, ByteBuffer buffer) {
        client.read(buffer, buffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer count, ByteBuffer current) {
                if (count == -1) {
                    close(client);
                    return;
                }
                if (count == 0) {
                    readAgain(client, current);
                    return;
                }
                current.flip();
                writeRemaining(client, current);
            }

            @Override
            public void failed(Throwable error, ByteBuffer current) {
                close(client);
            }
        });
    }

    private static void writeRemaining(
            AsynchronousSocketChannel client, ByteBuffer buffer) {
        client.write(buffer, buffer, new CompletionHandler<>() {
            @Override
            public void completed(Integer count, ByteBuffer current) {
                if (current.hasRemaining()) {
                    writeRemaining(client, current);
                } else {
                    current.clear();
                    readAgain(client, current);
                }
            }

            @Override
            public void failed(Throwable error, ByteBuffer current) {
                close(client);
            }
        });
    }

    private static void close(AsynchronousSocketChannel client) {
        try {
            client.close();
        } catch (IOException ignored) {
            // 已在关闭流程中。
        }
    }
}

