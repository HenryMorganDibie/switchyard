package com.henrymorgandibie.switchyard.messaging.kafka;

import java.util.UUID;

/**
 * Mirrors {@code TransactionEvent}'s own fields - deliberately not richer than the audit-trail
 * row it's published from. {@code fromState} is null for a transaction's first event, same as
 * the domain entity.
 */
public record TransactionEventPayload(UUID transactionId, String fromState, String toState, String detail) {
}
