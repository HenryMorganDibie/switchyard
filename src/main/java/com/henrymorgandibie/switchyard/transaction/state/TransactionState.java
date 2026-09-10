package com.henrymorgandibie.switchyard.transaction.state;

/**
 * The lifecycle states a {@code Transaction} can be in. This enum is the data type a
 * transaction's state is stored as; which transitions between these states are legal is
 * {@code TransactionStateMachine}'s responsibility (a separate, later milestone) - deliberately
 * kept apart from this type, which only names the possible states.
 *
 * <p>Payment systems track explicit transaction state rather than relying on HTTP-request
 * success/failure because a single logical transaction can span multiple network hops (switch
 * to issuer, possibly a reversal sent independently later) with outcomes that arrive
 * asynchronously or not at all (timeout) - "did the HTTP call to /authorize succeed" answers a
 * different question than "what is true about this transaction right now," and the latter is
 * what settlement, reconciliation, and duplicate-detection all depend on.
 */
public enum TransactionState {
    RECEIVED,
    VALIDATING,
    VALIDATED,
    ROUTING,
    SENT_TO_ISSUER,
    APPROVED,
    DECLINED,
    TIMEOUT,
    REVERSAL_PENDING,
    REVERSED,
    FAILED
}
