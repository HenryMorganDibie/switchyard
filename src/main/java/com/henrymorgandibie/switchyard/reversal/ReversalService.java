package com.henrymorgandibie.switchyard.reversal;

import com.henrymorgandibie.switchyard.messaging.kafka.TransactionEventPublisher;
import com.henrymorgandibie.switchyard.transaction.domain.Reversal;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.domain.TransactionEvent;
import com.henrymorgandibie.switchyard.transaction.repository.ReversalRepository;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionEventRepository;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import com.henrymorgandibie.switchyard.transaction.state.TransactionStateMachine;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.UUID;

/**
 * Links an incoming reversal (MTI 0400) to the original transaction its RRN (DE37) references,
 * and assigns switch-generated RRNs to approved transactions that didn't already carry one - see
 * {@link RrnGenerator}.
 *
 * <p>The interesting case this class exists for is the race the brief explicitly calls out: a
 * reversal for transaction X can arrive concurrently with X's own authorization completing (e.g.
 * a client that times out waiting for the 0210, assumes failure, and fires a reversal while the
 * original 0200 is still in flight to the issuer). Both paths update the same {@code Transaction}
 * row. Optimistic locking via {@code @Version} detects that collision as an {@link
 * ObjectOptimisticLockingFailureException} on whichever write loses the race; this service
 * retries by re-reading the fresh row rather than failing the reversal outright.
 */
public final class ReversalService {

    private static final int MAX_RETRIES = 5;

    private final TransactionRepository transactionRepository;
    private final ReversalRepository reversalRepository;
    private final TransactionEventRepository eventRepository;
    private final TransactionEventPublisher transactionEventPublisher;

    public ReversalService(TransactionRepository transactionRepository, ReversalRepository reversalRepository,
                            TransactionEventRepository eventRepository,
                            TransactionEventPublisher transactionEventPublisher) {
        this.transactionRepository = transactionRepository;
        this.reversalRepository = reversalRepository;
        this.eventRepository = eventRepository;
        this.transactionEventPublisher = transactionEventPublisher;
    }

    /**
     * Assigns a switch-generated RRN to a transaction that didn't already carry one, and returns
     * the freshly-managed instance - callers must use the return value for any further
     * persistence on this transaction, per the merge() staleness note on
     * {@code TransactionProcessingPipeline.transitionAndRecord}. No-op (returns the same instance
     * unchanged) if a client-supplied RRN is already present.
     */
    public Transaction assignRrnIfAbsent(Transaction transaction, String transmissionDateTime) {
        if (transaction.rrn() != null) {
            return transaction;
        }
        transaction.assignRrn(RrnGenerator.generate(transmissionDateTime, transaction.stan()));
        return transactionRepository.saveAndFlush(transaction);
    }

    /**
     * Records a reversal against the original transaction referenced by {@code referencedRrn},
     * and - if that original is currently APPROVED, TIMEOUT, or already REVERSAL_PENDING - drives
     * it the rest of the way to REVERSED.
     *
     * <p>The {@link Reversal} audit record is written exactly once regardless of contention; only
     * the original's state transitions are retried, since retrying the record insert too would
     * produce duplicate rows on every optimistic-lock conflict.
     *
     * <p>If the original isn't found, or is found but isn't in one of those three states (still
     * mid-flight through validation/routing/authorization, or already DECLINED/REVERSED/FAILED),
     * the link is still recorded but the original's state is left untouched - forcing REVERSED
     * from an arbitrary state would itself be an illegal transition, and a reversal for a
     * still-in-flight original has nothing yet to reverse. Catching such an original up once it
     * later reaches APPROVED is a natural extension, not built here.
     */
    public ReversalLinkResult linkToOriginal(String referencedRrn, UUID reversalTransactionId, String reason) {
        if (referencedRrn == null) {
            return new ReversalLinkResult.OriginalNotFound();
        }
        Transaction original = mostRecentByRrn(referencedRrn);
        if (original == null) {
            return new ReversalLinkResult.OriginalNotFound();
        }

        reversalRepository.saveAndFlush(Reversal.of(original.id(), reversalTransactionId, reason));

        original = advanceToward(original, TransactionState.REVERSAL_PENDING);
        if (original.state() == TransactionState.REVERSAL_PENDING) {
            original = advanceToward(original, TransactionState.REVERSED);
        }
        return new ReversalLinkResult.Linked(original.id());
    }

    /**
     * Retries {@code target}'s transition under optimistic-lock contention, recording the audit
     * event on success. If {@code original}'s current state can't legally reach {@code target}
     * (already there, still in flight, or already terminal some other way), returns it unchanged
     * - the graceful no-op path described on {@link #linkToOriginal}, not an error.
     */
    private Transaction advanceToward(Transaction original, TransactionState target) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            TransactionState from = original.state();
            if (!TransactionStateMachine.canTransition(from, target)) {
                return original;
            }
            try {
                TransactionStateMachine.transition(original, target);
                Transaction saved = transactionRepository.saveAndFlush(original);
                TransactionEvent event = TransactionEvent.of(saved.id(), from, target,
                        "reversal linked to original transaction");
                eventRepository.saveAndFlush(event);
                transactionEventPublisher.publish(event);
                return saved;
            } catch (ObjectOptimisticLockingFailureException raced) {
                UUID originalId = original.id();
                original = transactionRepository.findById(originalId)
                        .orElseThrow(() -> new IllegalStateException(
                                "original transaction " + originalId + " disappeared during reversal linking"));
            }
        }
        throw new IllegalStateException(
                "could not transition original " + original.id() + " to " + target + " after " + MAX_RETRIES
                        + " attempts - persistent contention");
    }

    private Transaction mostRecentByRrn(String rrn) {
        List<Transaction> matches = transactionRepository.findByRrnOrderByCreatedAtDesc(rrn);
        return matches.isEmpty() ? null : matches.get(0);
    }
}
