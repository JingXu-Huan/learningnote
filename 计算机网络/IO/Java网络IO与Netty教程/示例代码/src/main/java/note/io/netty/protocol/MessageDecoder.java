package note.io.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MessageDecoder
        extends MessageToMessageDecoder<ByteBuf> {
    @Override
    protected void decode(
            ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int magic = in.readInt();
        int version = in.readUnsignedByte();
        int type = in.readUnsignedByte();
        in.skipBytes(2);
        int requestId = in.readInt();
        int bodyLength = in.readInt();

        if (magic != MessageEncoder.MAGIC || version != 1) {
            throw new CorruptedFrameException("bad magic/version");
        }
        if (bodyLength < 0
                || bodyLength > MessageEncoder.MAX_BODY_LENGTH
                || bodyLength != in.readableBytes()) {
            throw new CorruptedFrameException(
                    "invalid body length: " + bodyLength);
        }

        String body = in.readCharSequence(
                bodyLength, StandardCharsets.UTF_8).toString();
        out.add(new Message(type, requestId, body));
    }
}

