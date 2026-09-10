package com.henrymorgandibie.switchyard.network.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class IsoServerInboundHandlerTest {

    @Test
    void writesTheHandlersResponseBackToTheChannel() {
        IsoMessageHandler handler = (correlationId, body) -> "RESPONSE".getBytes(StandardCharsets.US_ASCII);
        EmbeddedChannel channel = new EmbeddedChannel(new IsoServerInboundHandler(handler));

        channel.writeInbound(Unpooled.wrappedBuffer("REQUEST".getBytes(StandardCharsets.US_ASCII)));

        ByteBuf out = channel.readOutbound();
        assertThat(toBytes(out)).isEqualTo("RESPONSE".getBytes(StandardCharsets.US_ASCII));
        out.release();
    }

    @Test
    void generatesADistinctNonNullCorrelationIdPerMessage() {
        AtomicReference<String> lastId = new AtomicReference<>();
        Set<String> seen = new HashSet<>();
        IsoMessageHandler handler = (correlationId, body) -> {
            assertThat(correlationId).isNotNull().isNotBlank();
            seen.add(correlationId);
            lastId.set(correlationId);
            return body;
        };
        EmbeddedChannel channel = new EmbeddedChannel(new IsoServerInboundHandler(handler));

        for (int i = 0; i < 5; i++) {
            channel.writeInbound(Unpooled.wrappedBuffer(("MSG" + i).getBytes(StandardCharsets.US_ASCII)));
            ByteBuf out = channel.readOutbound();
            out.release();
        }

        assertThat(seen).hasSize(5);
    }

    @Test
    void closesTheChannelWhenTheHandlerThrows() {
        IsoMessageHandler handler = (correlationId, body) -> {
            throw new RuntimeException("boom");
        };
        EmbeddedChannel channel = new EmbeddedChannel(new IsoServerInboundHandler(handler));

        channel.writeInbound(Unpooled.wrappedBuffer("REQUEST".getBytes(StandardCharsets.US_ASCII)));

        assertThat(channel.isOpen()).isFalse();
    }

    private static byte[] toBytes(ByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }
}
