package note.io.netty.protocol;

import java.util.Objects;

public record Message(int type, int requestId, String body) {
    public Message {
        if (type < 0 || type > 255) {
            throw new IllegalArgumentException("type must fit unsigned byte");
        }
        Objects.requireNonNull(body, "body");
    }
}

