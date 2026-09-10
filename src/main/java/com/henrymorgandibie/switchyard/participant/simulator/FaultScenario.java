package com.henrymorgandibie.switchyard.participant.simulator;

/**
 * The downstream-issuer behaviors switchyard's simulator can be made to produce. Each maps to a
 * specific, deliberate outcome - never a generic caught-exception error - documented alongside
 * {@code FaultInjectionRegistry} and applied by {@code TransactionProcessingPipeline}.
 *
 * <p>"Duplicate response" from the brief's original fault list is deliberately not modeled here:
 * {@link com.henrymorgandibie.switchyard.participant.issuer.IssuerConnector#authorize} is a
 * single synchronous call with a single return value, so there is no channel on which a
 * response could arrive twice. The meaningful version of "duplicate" - the same client request
 * arriving twice - is an idempotency concern, not an issuer-fault one, and belongs to the
 * upcoming idempotency milestone's IdempotencyService.
 */
public enum FaultScenario {
    /** Normal, fast approval. */
    APPROVE,
    /** Generic decline ("do not honor"). */
    DECLINE,
    INSUFFICIENT_FUNDS,
    INVALID_ACCOUNT,
    /** The issuer refuses the call outright - a clean, known failure: nothing was processed. */
    ISSUER_UNAVAILABLE,
    /** The connection drops mid-call - the outcome is genuinely unknown, not a clean failure. */
    CONNECTION_RESET,
    /** The issuer never responds within the pipeline's configured time budget. */
    TIMEOUT,
    /** Succeeds, but only after an artificial delay comfortably under the timeout budget. */
    DELAYED_APPROVAL,
    /** The issuer returns a response with a response code that cannot be trusted/parsed. */
    MALFORMED_RESPONSE
}
