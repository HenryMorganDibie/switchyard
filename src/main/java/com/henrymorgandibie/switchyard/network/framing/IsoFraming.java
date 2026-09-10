package com.henrymorgandibie.switchyard.network.framing;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

/**
 * switchyard's TCP wire framing: a 2-byte big-endian length prefix followed by the raw ISO 8583
 * message body (see {@code com.henrymorgandibie.switchyard.iso8583.codec}). Framing is
 * necessary because ISO 8583 itself has no self-terminating end-of-message marker - the codec
 * only knows a message is complete once every bit in its bitmap has a corresponding field, which
 * requires having the whole message in hand first. A single TCP {@code read()} is not
 * guaranteed to return exactly one message: the OS may split one message across several reads
 * (fragmentation, e.g. under network congestion or a slow/chunked sender) or deliver several
 * small messages coalesced into a single read (Nagle's algorithm, or a fast sender simply
 * getting ahead of a slower reader). A length-prefixed frame is what lets a reader reliably
 * reconstruct message boundaries regardless of how the underlying reads happen to be chopped up.
 *
 * <p>Both directions are implemented using Netty's own {@link LengthFieldBasedFrameDecoder} and
 * {@link LengthFieldPrepender} rather than a hand-rolled buffering loop: these are Netty's
 * battle-tested, exactly-once-per-frame primitives for this. Decoder parameters: length field at
 * offset 0, length field is 2 bytes, no length adjustment (the length prefix counts only the
 * body, not itself), and the 2-byte header is stripped from the frame delivered downstream so
 * handlers only ever see a complete message body.
 */
public final class IsoFraming {

    private static final int LENGTH_FIELD_LENGTH = 2;
    private static final int LENGTH_FIELD_OFFSET = 0;
    private static final int LENGTH_ADJUSTMENT = 0;
    private static final int INITIAL_BYTES_TO_STRIP = 2;

    private IsoFraming() {
    }

    /**
     * A new frame decoder instance. Must be a fresh instance per channel - like all Netty
     * {@code ByteToMessageDecoder}s, it is stateful (it buffers partial frames) and is not
     * shareable across channels/threads.
     *
     * @param maxFrameLength the largest body length switchyard will accept; a declared length
     *                       beyond this throws {@link io.netty.handler.codec.TooLongFrameException}
     *                       rather than allocating unbounded memory for a malformed or hostile
     *                       length prefix
     */
    public static LengthFieldBasedFrameDecoder newFrameDecoder(int maxFrameLength) {
        return new LengthFieldBasedFrameDecoder(
                maxFrameLength, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP);
    }

    /** A new frame prepender instance; {@link LengthFieldPrepender} is stateless and shareable, but a fresh instance keeps channel pipelines symmetric and simple to reason about. */
    public static LengthFieldPrepender newFramePrepender() {
        return new LengthFieldPrepender(LENGTH_FIELD_LENGTH);
    }
}
