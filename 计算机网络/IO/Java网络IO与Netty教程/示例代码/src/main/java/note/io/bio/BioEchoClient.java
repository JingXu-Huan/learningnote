package note.io.bio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class BioEchoClient {
    private BioEchoClient() {
    }

    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 9000 : Integer.parseInt(args[0]);
        try (Socket socket = new Socket("127.0.0.1", port);
             var reader = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8));
             var writer = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write("hello io");
            writer.newLine();
            writer.flush();
            System.out.println(reader.readLine());
        }
    }
}

