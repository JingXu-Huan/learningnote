package note.io.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

public final class NioEchoServer {
    private static final int PORT = 9001;

    private NioEchoServer() {
    }

    public static void main(String[] args) throws IOException {
        try (Selector selector = Selector.open();
             ServerSocketChannel server = ServerSocketChannel.open()) {
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(PORT));
            server.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("NIO Echo listening on " + PORT);

            while (true) {
                selector.select();
                Iterator<SelectionKey> iterator =
                        selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    try {
                        if (key.isAcceptable()) {
                            accept(server, selector);
                        }
                        if (key.isValid() && key.isReadable()) {
                            read(key);
                        }
                        if (key.isValid() && key.isWritable()) {
                            write(key);
                        }
                    } catch (IOException error) {
                        close(key);
                    }
                }
            }
        }
    }

    private static void accept(
            ServerSocketChannel server, Selector selector) throws IOException {
        SocketChannel client;
        while ((client = server.accept()) != null) {
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ,
                    new ConnectionState());
        }
    }

    private static void read(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ConnectionState state = (ConnectionState) key.attachment();
        int count = client.read(state.readBuffer);
        if (count == -1) {
            close(key);
            return;
        }
        if (count == 0) {
            return;
        }

        state.readBuffer.flip();
        ByteBuffer response = ByteBuffer.allocate(state.readBuffer.remaining());
        response.put(state.readBuffer).flip();
        state.pendingWrites.add(response);
        state.readBuffer.compact();
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }

    private static void write(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ConnectionState state = (ConnectionState) key.attachment();
        while (!state.pendingWrites.isEmpty()) {
            ByteBuffer current = state.pendingWrites.peek();
            client.write(current);
            if (current.hasRemaining()) {
                return;
            }
            state.pendingWrites.remove();
        }
        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
    }

    private static void close(SelectionKey key) {
        key.cancel();
        try {
            key.channel().close();
        } catch (IOException ignored) {
            // 已在关闭流程中。
        }
    }

    private static final class ConnectionState {
        private final ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        private final Queue<ByteBuffer> pendingWrites = new ArrayDeque<>();
    }
}

