package com.henrymorgandibie.switchyard.iso8583.codec;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supplementary checks kept deliberately separate from {@link IsoMessageFixtureTest}: those
 * fixtures prove correctness against independently hand-computed expected bytes, which is the
 * primary evidence. The {@code pack(unpack(x)) == x} round trip here is real and useful but not
 * sufficient alone - a packer and unpacker that agree with each other can still both be wrong -
 * so it is additional evidence, not the main proof. This file also demonstrates determinism
 * (no dependence on iteration order or other hidden state) and thread-safety (the codec classes
 * hold no mutable state, so a shared instance/static methods are safe to call concurrently).
 */
class IsoMessageRoundTripSupplementaryTest {

    private static final byte[] FIXTURE_0200_BYTES = ("0200" + "7220000100808000"
            + "16" + "4111111111111111" + "000000" + "000000005000" + "0910120000" + "000001"
            + "05" + "12345" + "TERM0001" + "566").getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @Test
    void packOfUnpackReturnsTheOriginalBytes() {
        IsoMessage message = IsoMessageUnpacker.unpack(FIXTURE_0200_BYTES);
        assertThat(IsoMessagePacker.pack(message)).isEqualTo(FIXTURE_0200_BYTES);
    }

    @Test
    void packingTheSameLogicalMessageTwiceProducesIdenticalBytes() {
        IsoMessage first = buildSampleMessage("000001");
        IsoMessage second = buildSampleMessage("000001");
        assertThat(IsoMessagePacker.pack(first)).isEqualTo(IsoMessagePacker.pack(second));
    }

    @RepeatedTest(5)
    void fieldInsertionOrderDoesNotAffectPackedBytes() {
        IsoMessage inOrder = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000").numeric(4, "000000005000").numeric(11, "000001").build();
        IsoMessage reversedOrder = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(11, "000001").numeric(4, "000000005000").numeric(3, "000000").build();
        assertThat(IsoMessagePacker.pack(inOrder)).isEqualTo(IsoMessagePacker.pack(reversedOrder));
    }

    @Test
    @Timeout(30)
    void codecIsThreadSafeUnderConcurrentPackAndUnpack() throws Exception {
        int threads = 16;
        int iterationsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                    .<Callable<Boolean>>mapToObj(threadIndex -> () -> {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            String stan = String.format("%06d", (threadIndex * iterationsPerThread + i) % 1_000_000);
                            IsoMessage built = buildSampleMessage(stan);
                            byte[] packed = IsoMessagePacker.pack(built);
                            IsoMessage roundTripped = IsoMessageUnpacker.unpack(packed);
                            if (!roundTripped.stringField(11).equals(stan)) {
                                return false;
                            }
                            if (!java.util.Arrays.equals(IsoMessagePacker.pack(roundTripped), packed)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .toList();

            List<Future<Boolean>> results = pool.invokeAll(tasks);
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static IsoMessage buildSampleMessage(String stan) {
        return IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(2, "4111111111111111")
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, stan)
                .numeric(32, "12345")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
    }
}
