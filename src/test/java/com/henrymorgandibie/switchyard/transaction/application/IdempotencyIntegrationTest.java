package com.henrymorgandibie.switchyard.transaction.application;

import com.henrymorgandibie.switchyard.idempotency.IdempotencyKeys;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the five idempotency scenarios from the plan's idempotency design against the real,
 * fully-wired switch (real Postgres via {@code docker compose up}, same pattern as the
 * transaction-domain milestone's repository tests; real Redis too, unlike the golden-path test -
 * this is the one test in the suite that specifically needs Redis up to exercise the fast-path
 * cache, not just the Postgres fallback). Drives {@code TransactionProcessingPipeline} directly
 * (in-process method calls) rather than over a real socket - the golden-path test already proves
 * the TCP round trip; this test's job is proving the idempotency guarantee itself, for which the
 * TCP layer is incidental.
 *
 * <p>DemoIssuer generates a fresh random authorization id (DE38) on every real call, which this
 * test uses as a concrete signal that no reprocessing occurred: if a "duplicate" response's DE38
 * matched the first response's DE38, the two calls must have produced the same outcome without a
 * second independent issuer call - a coincidentally-identical random id is not a real risk here.
 */
@SpringBootTest
@TestPropertySource(properties = "switchyard.tcp.enabled=false")
class IdempotencyIntegrationTest {

    /**
     * Idempotency keys are deterministic (same fields -&gt; same SHA-256 hash), and this test runs
     * against the long-lived docker-compose Postgres/Redis rather than a fresh Testcontainers
     * instance, so fixed STANs would collide with whatever this same test left behind on its
     * previous run - found the hard way when a rerun's "first" call silently hit a stale Redis
     * entry from the previous run's retry step. A time-varying 3-digit prefix (STANs are a fixed
     * 6 digits; the other 3 identify which test within this run) guarantees every run uses fresh
     * identifying fields, so every run's idempotency keys are genuinely new.
     */
    private static final String RUN_PREFIX = String.format("%03d", System.currentTimeMillis() % 1000);

    @Autowired
    private TransactionProcessingPipeline pipeline;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void sameRequestArrivingTwiceIsProcessedOnce() {
        String stan = RUN_PREFIX + "001";
        byte[] request = financialRequest(stan, "0910130001", "000000005000");

        IsoMessage first = IsoMessageUnpacker.unpack(pipeline.handle("corr-a1", request));
        IsoMessage second = IsoMessageUnpacker.unpack(pipeline.handle("corr-a2", request));

        assertThat(second.stringField(39)).isEqualTo(first.stringField(39));
        assertThat(second.stringField(38)).isEqualTo(first.stringField(38)); // same DE38 -> not reprocessed
        assertThat(countTransactionsWithStan(stan)).isEqualTo(1);
    }

    @Test
    void firstResponseLostThenClientRetriesStillReturnsTheOriginalOutcomeWithoutReprocessing() {
        String stan = RUN_PREFIX + "002";
        byte[] request = financialRequest(stan, "0910130002", "000000005000");
        String idempotencyKey = IdempotencyKeys.compute("12345", "TERM0001", stan, "0910130002", "000000", 5000L);

        IsoMessage first = IsoMessageUnpacker.unpack(pipeline.handle("corr-b1", request));
        assertThat(first.stringField(38)).isNotEmpty(); // the original response did carry a real auth id
        // Simulate "the response never reached the client" by evicting the fast-path cache entry
        // - the retry is then forced down the Postgres-lookup fallback path instead of the Redis
        // hit, exactly the path the plan calls out as the durable, slower-but-correct guarantee.
        redisTemplate.delete("idem:" + idempotencyKey);

        IsoMessage retried = IsoMessageUnpacker.unpack(pipeline.handle("corr-b2", request));

        assertThat(retried.stringField(39)).isEqualTo(first.stringField(39));
        // The Postgres-fallback path (unlike a Redis cache hit) doesn't have DE38 to replay - see
        // TransactionProcessingPipeline.respondToDuplicate's Javadoc. The real proof this wasn't
        // reprocessed is that exactly one transaction row exists for this STAN after two calls:
        // a second real issuer call would either have produced a second row or hit the same
        // unique-constraint path again, not silently vanished.
        assertThat(retried.hasField(38)).isFalse();
        assertThat(countTransactionsWithStan(stan)).isEqualTo(1);
    }

    @Test
    void reversalArrivingAfterOriginalApprovalIsProcessedAsItsOwnTransaction() {
        String originalStan = RUN_PREFIX + "003";
        String reversalStan = RUN_PREFIX + "103";
        byte[] original = financialRequest(originalStan, "0910130003", "000000005000");
        IsoMessage originalResponse = IsoMessageUnpacker.unpack(pipeline.handle("corr-c1", original));
        assertThat(originalResponse.stringField(39)).isEqualTo("00");

        byte[] reversal = reversalRequest(reversalStan, "0910130004", "000000005000");
        IsoMessage reversalResponse = IsoMessageUnpacker.unpack(pipeline.handle("corr-c2", reversal));

        // The reversal has its own distinct identifying fields (different STAN/processing code),
        // so it gets its own idempotency key and is not rejected as a false-positive duplicate of
        // the original it references.
        assertThat(reversalResponse.mti()).isEqualTo(Mti.REVERSAL_RESPONSE);
        assertThat(countTransactionsWithStan(originalStan)).isEqualTo(1);
        assertThat(countTransactionsWithStan(reversalStan)).isEqualTo(1);
    }

    @Test
    void duplicateReversalIsProcessedOnce() {
        String stan = RUN_PREFIX + "005";
        byte[] reversal = reversalRequest(stan, "0910130005", "000000005000");

        IsoMessage first = IsoMessageUnpacker.unpack(pipeline.handle("corr-d1", reversal));
        IsoMessage second = IsoMessageUnpacker.unpack(pipeline.handle("corr-d2", reversal));

        assertThat(second.stringField(39)).isEqualTo(first.stringField(39));
        assertThat(countTransactionsWithStan(stan)).isEqualTo(1);
    }

    @Test
    void twoLegitimateTransactionsSharingAStanBothSucceed() {
        String sharedStan = RUN_PREFIX + "006";
        byte[] first = financialRequest(sharedStan, "0910130006", "000000005000");
        byte[] second = financialRequest(sharedStan, "0910130106", "000000006000"); // different time+amount

        IsoMessage firstResponse = IsoMessageUnpacker.unpack(pipeline.handle("corr-e1", first));
        IsoMessage secondResponse = IsoMessageUnpacker.unpack(pipeline.handle("corr-e2", second));

        assertThat(firstResponse.stringField(39)).isEqualTo("00");
        assertThat(secondResponse.stringField(39)).isEqualTo("00");
        assertThat(countTransactionsWithStan(sharedStan)).isEqualTo(2);
    }

    private long countTransactionsWithStan(String stan) {
        return transactionRepository.findAll().stream().filter(t -> t.stan().equals(stan)).count();
    }

    private static byte[] financialRequest(String stan, String transmissionDateTime, String amount) {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(4, amount)
                .numeric(7, transmissionDateTime)
                .numeric(11, stan)
                .numeric(32, "12345")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
        return IsoMessagePacker.pack(message);
    }

    private static byte[] reversalRequest(String stan, String transmissionDateTime, String amount) {
        IsoMessage message = IsoMessage.builder(Mti.REVERSAL_REQUEST)
                .numeric(3, "020000")
                .numeric(4, amount)
                .numeric(7, transmissionDateTime)
                .numeric(11, stan)
                .numeric(32, "12345")
                .ans(37, "RRN000000001")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
        return IsoMessagePacker.pack(message);
    }
}
