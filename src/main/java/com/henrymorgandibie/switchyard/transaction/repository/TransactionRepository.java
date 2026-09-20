package com.henrymorgandibie.switchyard.transaction.repository;

import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** The idempotency check's lookup path - backed by the unique index on idempotency_key. */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /** Traces a TCP-gateway correlation id back to its persisted transaction. */
    Optional<Transaction> findByCorrelationId(String correlationId);

    /**
     * A reversal's lookup path for the original transaction it references. Returns a list, not
     * an Optional, because rrn has no unique constraint: switch-assigned RRNs are derived
     * deterministically (see RrnGenerator) rather than sequenced, so a collision is possible
     * though very unlikely - ReversalService resolves that case by preferring the most recent
     * match rather than the lookup itself throwing on multiple results.
     */
    List<Transaction> findByRrnOrderByCreatedAtDesc(String rrn);
}
