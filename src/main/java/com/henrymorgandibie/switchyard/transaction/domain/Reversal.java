package com.henrymorgandibie.switchyard.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Links an original transaction to the reversal transaction created for it.
 * {@code reversalTransactionId} is nullable: a reversal can be recorded as pending before the
 * reversal transaction itself has been created and linked.
 */
@Entity
@Table(name = "reversals")
public class Reversal {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "original_transaction_id", nullable = false, updatable = false)
    private UUID originalTransactionId;

    @Column(name = "reversal_transaction_id")
    private UUID reversalTransactionId;

    @Column(name = "reason", nullable = false, length = 255, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reversal() {
        // JPA
    }

    public static Reversal of(UUID originalTransactionId, UUID reversalTransactionId, String reason) {
        Reversal reversal = new Reversal();
        reversal.originalTransactionId = originalTransactionId;
        reversal.reversalTransactionId = reversalTransactionId;
        reversal.reason = reason;
        reversal.createdAt = Instant.now();
        return reversal;
    }

    public void linkReversalTransaction(UUID reversalTransactionId) {
        this.reversalTransactionId = reversalTransactionId;
    }

    public UUID id() {
        return id;
    }

    public UUID originalTransactionId() {
        return originalTransactionId;
    }

    public UUID reversalTransactionId() {
        return reversalTransactionId;
    }

    public String reason() {
        return reason;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
