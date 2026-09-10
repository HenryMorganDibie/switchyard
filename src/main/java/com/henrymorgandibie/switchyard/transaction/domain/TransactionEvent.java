package com.henrymorgandibie.switchyard.transaction.domain;

import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One state transition in a transaction's audit trail. {@code fromState} is null for the first
 * event (there is no prior state before RECEIVED).
 */
@Entity
@Table(name = "transaction_events")
public class TransactionEvent {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 32, updatable = false)
    private TransactionState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 32, updatable = false)
    private TransactionState toState;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "detail", updatable = false)
    private String detail;

    protected TransactionEvent() {
        // JPA
    }

    public static TransactionEvent of(UUID transactionId, TransactionState fromState,
                                       TransactionState toState, String detail) {
        TransactionEvent event = new TransactionEvent();
        event.transactionId = transactionId;
        event.fromState = fromState;
        event.toState = toState;
        event.detail = detail;
        event.occurredAt = Instant.now();
        return event;
    }

    public UUID id() {
        return id;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public TransactionState fromState() {
        return fromState;
    }

    public TransactionState toState() {
        return toState;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public String detail() {
        return detail;
    }
}
