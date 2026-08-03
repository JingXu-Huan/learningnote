package note.io.netty.protocol;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public final class ProtocolPipeline {
    private ProtocolPipeline() {
    }

    public static void install(ChannelPipeline pipeline) {
        pipeline.addLast("frameDecoder",
                new LengthFieldBasedFrameDecoder(
                        MessageEncoder.HEADER_LENGTH
                                + MessageEncoder.MAX_BODY_LENGTH,
                        12,
                        4,
                        0,
                        0));
        pipeline.addLast("messageDecoder", new MessageDecoder());
        pipeline.addLast("messageEncoder", new MessageEncoder());
    }
}

