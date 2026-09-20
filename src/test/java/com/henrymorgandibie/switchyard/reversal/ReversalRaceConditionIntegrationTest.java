package com.henrymorgandibie.switchyard.reversal;

import com.henrymorgandibie.switchyard.transaction.domain.Reversal;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.repository.ReversalRepository;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link ReversalService#linkToOriginal}'s optimistic-lock retry against a real,
 * unmocked Postgres row under genuine concurrent contention - not a mocked exception, actual
 * threads racing an actual {@code UPDATE ... WHERE id = ? AND version = ?}.
 *
 * <p>The scenario is several concurrent reversal messages for the <em>same already-approved
 * original</em> - e.g. a client retransmitting a 0400 over more than one connection, or two
 * different channels reversing the same RRN near-simultaneously. This is the scenario that
 * genuinely produces a Postgres-level version conflict in this architecture: {@link
 * ReversalService#linkToOriginal} only attempts a write once its own read of the original
 * already shows a state a reversal can legally act on (APPROVED, TIMEOUT, or REVERSAL_PENDING -
 * see {@code advanceToward}'s {@code canTransition} guard), so for two callers to genuinely race
 * on the same row version, both must have read an already-terminal-enough state - which is
 * exactly what happens when multiple reversals target the same completed original at once.
 *
 * <p>A reversal racing the <em>original transaction's own</em> completion (its
 * {@code SENT_TO_ISSUER -&gt; APPROVED} transition inside {@code TransactionProcessingPipeline})
 * is a related but structurally different case: {@code TransactionProcessingPipeline} does not
 * itself retry on a lock conflict, so if that specific write loses a race it propagates as an
 * unhandled exception on the original request rather than being absorbed - a known, documented
 * limitation of this milestone's scope, not something this test claims to cover.
 */
@SpringBootTest
@TestPropertySource(properties = "switchyard.tcp.enabled=false")
class ReversalRaceConditionIntegrationTest {

    private static final int CONCURRENT_REVERSALS = 10;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ReversalRepository reversalRepository;

    @Autowired
    private ReversalService reversalService;

    @Test
    @Timeout(30)
    void concurrentReversalsForTheSameOriginalAllConvergeToReversedUnderRealLockContention() throws Exception {
        String rrn = "RACE" + (System.currentTimeMillis() % 100_000_000L);
        Transaction original = Transaction.received("corr-race-" + UUID.randomUUID(), "0200", "900001", rrn,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-race-" + UUID.randomUUID());
        original.applyState(TransactionState.APPROVED);
        Transaction saved = transactionRepository.saveAndFlush(original);

        // reversal_transaction_id carries a real foreign key to transactions - in production this
        // is always the incoming reversal message's own, already-persisted Transaction row (see
        // TransactionProcessingPipeline.handle, which inserts it before ever calling
        // ReversalService), so each concurrent reversal here needs one too, not a bare random id.
        // Its own rrn column is deliberately left null - a reversal's DE37 references the
        // original's RRN, it is not this row's own RRN (see the fix in
        // TransactionProcessingPipeline.handle's rrn extraction, found via this very test: an
        // earlier version of this fixture set rrn here too, which made
        // ReversalService.mostRecentByRrn's lookup match these reversal rows themselves - always
        // more recently created than the original - instead of the actual original).
        List<UUID> reversalTransactionIds = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REVERSALS; i++) {
            Transaction reversalTransaction = Transaction.received("corr-race-reversal-" + i, "0400",
                    "90" + String.format("%04d", i), null, "020000", 1000L, "566", "TERM0001", "12345",
                    "idem-race-reversal-" + UUID.randomUUID());
            reversalTransactionIds.add(transactionRepository.saveAndFlush(reversalTransaction).id());
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REVERSALS);
        CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_REVERSALS);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_REVERSALS; i++) {
            UUID reversalTransactionId = reversalTransactionIds.get(i);
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    reversalService.linkToOriginal(rrn, reversalTransactionId, "concurrent reversal");
                } catch (Throwable t) {
                    errors.add(t);
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(errors)
                .as("every concurrent linkToOriginal call must either succeed or be transparently retried, "
                        + "never surface a raw lock-conflict exception")
                .isEmpty();

        Transaction reloaded = transactionRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.state()).isEqualTo(TransactionState.REVERSED);

        List<Reversal> reversals = reversalRepository.findByOriginalTransactionId(saved.id());
        assertThat(reversals)
                .as("each of the %d concurrent reversal messages gets its own audit record - "
                        + "de-duplicating identical messages is the idempotency layer's job, one level up "
                        + "in TransactionProcessingPipeline, not ReversalService's", CONCURRENT_REVERSALS)
                .hasSize(CONCURRENT_REVERSALS);
    }
}
