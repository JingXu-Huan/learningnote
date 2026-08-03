package note.io.bio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class BioEchoServer {
    private static final int PORT = 9000;

    private BioEchoServer() {
    }

    public static void main(String[] args) throws IOException {
        ThreadPoolExecutor workers = new ThreadPoolExecutor(
                4,
                16,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.AbortPolicy());

        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("BIO Echo listening on " + PORT);
            while (!workers.isShutdown()) {
                Socket client = server.accept();
                try {
                    workers.execute(() -> handle(client));
                } catch (RejectedExecutionException rejected) {
                    client.close();
                }
            }
        } finally {
            workers.shutdown();
        }
    }

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
            System.out.println("idle client: " + socket);
        } catch (IOException error) {
            System.out.println("connection closed: " + error.getMessage());
        }
    }
}

