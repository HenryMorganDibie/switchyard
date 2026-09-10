package com.henrymorgandibie.switchyard.transaction.repository;

import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** The idempotency check's lookup path - backed by the unique index on idempotency_key. */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /** Traces a TCP-gateway correlation id back to its persisted transaction. */
    Optional<Transaction> findByCorrelationId(String correlationId);
}
