package note.io.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

public final class MessageEncoder extends MessageToByteEncoder<Message> {
    public static final int MAGIC = 0x4C4E494F;
    public static final int HEADER_LENGTH = 16;
    public static final int MAX_BODY_LENGTH = 1024 * 1024;

    @Override
    protected void encode(
            ChannelHandlerContext ctx, Message message, ByteBuf out) {
        byte[] body = message.body().getBytes(StandardCharsets.UTF_8);
        if (body.length > MAX_BODY_LENGTH) {
            throw new EncoderException("body too large: " + body.length);
        }
        out.writeInt(MAGIC);
        out.writeByte(1);
        out.writeByte(message.type());
        out.writeShort(0);
        out.writeInt(message.requestId());
        out.writeInt(body.length);
        out.writeBytes(body);
    }
}

