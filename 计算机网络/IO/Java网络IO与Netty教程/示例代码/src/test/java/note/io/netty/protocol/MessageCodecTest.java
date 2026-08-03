package note.io.netty.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCodecTest {
    @Test
    void shouldRoundTripUnicodeMessage() {
        Message source = new Message(1, 42, "你好，Netty");
        ByteBuf frame = encode(source);

        EmbeddedChannel decoder = newDecoderChannel();
        assertTrue(decoder.writeInbound(frame));
        assertEquals(source, decoder.readInbound());
        assertFalse(decoder.finishAndReleaseAll());
    }

    @Test
    void shouldDecodeFrameArrivingInThreeParts() {
        EmbeddedChannel decoder = newDecoderChannel();
        ByteBuf frame = encode(new Message(1, 7, "half packet"));
        try {
            assertFalse(decoder.writeInbound(frame.readRetainedSlice(3)));
            assertFalse(decoder.writeInbound(frame.readRetainedSlice(8)));
            assertTrue(decoder.writeInbound(
                    frame.readRetainedSlice(frame.readableBytes())));

            Message decoded = decoder.readInbound();
            assertEquals(7, decoded.requestId());
            assertEquals("half packet", decoded.body());
        } finally {
            frame.release();
            decoder.finishAndReleaseAll();
        }
    }

    @Test
    void shouldDecodeTwoFramesFromOneInput() {
        ByteBuf first = encode(new Message(1, 1, "one"));
        ByteBuf second = encode(new Message(1, 2, "two"));
        ByteBuf merged = first.alloc().buffer(
                first.readableBytes() + second.readableBytes());
        merged.writeBytes(first).writeBytes(second);
        first.release();
        second.release();

        EmbeddedChannel decoder = newDecoderChannel();
        assertTrue(decoder.writeInbound(merged));
        assertEquals(1, ((Message) decoder.readInbound()).requestId());
        assertEquals(2, ((Message) decoder.readInbound()).requestId());
        assertFalse(decoder.finishAndReleaseAll());
    }

    private static ByteBuf encode(Message message) {
        EmbeddedChannel encoder = new EmbeddedChannel(new MessageEncoder());
        assertTrue(encoder.writeOutbound(message));
        ByteBuf encoded = encoder.readOutbound();
        assertFalse(encoder.finishAndReleaseAll());
        return encoded;
    }

    private static EmbeddedChannel newDecoderChannel() {
        return new EmbeddedChannel(
                new LengthFieldBasedFrameDecoder(
                        MessageEncoder.HEADER_LENGTH
                                + MessageEncoder.MAX_BODY_LENGTH,
                        12,
                        4,
                        0,
                        0),
                new MessageDecoder());
    }
}

