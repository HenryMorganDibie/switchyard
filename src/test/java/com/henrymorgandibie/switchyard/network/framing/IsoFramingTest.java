package com.henrymorgandibie.switchyard.network.framing;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses Netty's {@link EmbeddedChannel} - the standard way to unit-test a Netty pipeline without
 * real sockets - to prove switchyard's framing handles fragmentation (one message split across
 * several writes) and coalescing (several messages delivered in a single write), the two things
 * that make "one TCP read is not one ISO 8583 message" concrete. {@code IsoTcpServerTest}
 * separately proves the same properties hold over real sockets.
 */
class IsoFramingTest {

    @Test
    void decodesACompleteFrameDeliveredInOneWrite() {
        EmbeddedChannel channel = new EmbeddedChannel(IsoFraming.newFrameDecoder(1024));
        byte[] body = "0200TESTBODY".getBytes(StandardCharsets.US_ASCII);

        channel.writeInbound(framed(body));

        ByteBuf decoded = channel.readInbound();
        assertThat(decoded).isNotNull();
        assertThat(toBytes(decoded)).isEqualTo(body);
        decoded.release();
    }

    @Test
    void doesNotEmitAFrameUntilAllBytesHaveArrived() {
        EmbeddedChannel channel = new EmbeddedChannel(IsoFraming.newFrameDecoder(1024));
        byte[] body = "0200FRAGMENTEDMESSAGEBODY".getBytes(StandardCharsets.US_ASCII);
        ByteBuf complete = framed(body);

        // Feed the frame one byte at a time - the decoder must not emit anything until the
        // final byte arrives, proving it does not assume one read == one message.
        while (complete.readableBytes() > 1) {
            channel.writeInbound(complete.readBytes(1));
            assertThat((ByteBuf) channel.readInbound()).as("no frame should be emitted yet").isNull();
        }
        channel.writeInbound(complete.readBytes(1));

        ByteBuf decoded = channel.readInbound();
        assertThat(decoded).isNotNull();
        assertThat(toBytes(decoded)).isEqualTo(body);
        decoded.release();
    }

    @Test
    void decodesTwoFramesCoalescedIntoASingleWrite() {
        EmbeddedChannel channel = new EmbeddedChannel(IsoFraming.newFrameDecoder(1024));
        byte[] first = "0200FIRST".getBytes(StandardCharsets.US_ASCII);
        byte[] second = "0210SECOND".getBytes(StandardCharsets.US_ASCII);

        ByteBuf coalesced = Unpooled.wrappedBuffer(framed(first), framed(second));
        channel.writeInbound(coalesced);

        ByteBuf firstDecoded = channel.readInbound();
        ByteBuf secondDecoded = channel.readInbound();
        assertThat(toBytes(firstDecoded)).isEqualTo(first);
        assertThat(toBytes(secondDecoded)).isEqualTo(second);
        firstDecoded.release();
        secondDecoded.release();
    }

    @Test
    void rejectsAFrameDeclaringALengthBeyondTheConfiguredMaximum() {
        EmbeddedChannel channel = new EmbeddedChannel(IsoFraming.newFrameDecoder(16));
        byte[] body = new byte[100];

        assertThatThrownBy(() -> channel.writeInbound(framed(body)))
                .isInstanceOf(TooLongFrameException.class);
    }

    @Test
    void prependerWritesAHandComputedTwoByteBigEndianLengthPrefix() {
        EmbeddedChannel channel = new EmbeddedChannel(IsoFraming.newFramePrepender());
        byte[] body = "0200TEST".getBytes(StandardCharsets.US_ASCII); // 8 bytes -> prefix 0x00 0x08

        channel.writeOutbound(Unpooled.wrappedBuffer(body));

        // LengthFieldPrepender emits the length header and the original message body as two
        // separate outbound buffers (not one combined buffer) - on a real channel these are
        // written to the same TCP stream back-to-back, which is what IsoTcpServerTest proves
        // over a real socket. Read both here and concatenate to check the resulting wire bytes.
        ByteBuf header = channel.readOutbound();
        ByteBuf bodyBuf = channel.readOutbound();
        byte[] headerBytes = toBytes(header);
        byte[] bodyBytes = toBytes(bodyBuf);

        assertThat(headerBytes).hasSize(2);
        assertThat(headerBytes[0]).isEqualTo((byte) 0x00);
        assertThat(headerBytes[1]).isEqualTo((byte) 0x08);
        assertThat(bodyBytes).isEqualTo(body);
        header.release();
        bodyBuf.release();
    }

    private static ByteBuf framed(byte[] body) {
        ByteBuf buf = Unpooled.buffer(2 + body.length);
        buf.writeShort(body.length);
        buf.writeBytes(body);
        return buf;
    }

    private static byte[] toBytes(ByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }
}
