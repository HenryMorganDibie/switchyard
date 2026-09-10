package com.henrymorgandibie.switchyard.transaction.domain;

import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One logical ISO 8583 transaction: the request that created it, its current lifecycle state,
 * and the fields needed for idempotency, routing, and response construction later.
 *
 * <p>Several fields are nullable because not every MTI carries them - a network management
 * (0800/0810) exchange has no amount, currency, processing code, terminal, or acquiring
 * institution, for instance.
 *
 * <p>Cross-references to other entities ({@code TransactionEvent}, {@code IsoMessageRecord},
 * {@code Reversal}) are plain foreign-key UUID columns on those entities, not JPA
 * {@code @OneToMany}/{@code @ManyToOne} associations - deliberately: no code queries across
 * those relationships yet, and committing to a fetch strategy (lazy vs. eager) ahead of an
 * actual access pattern is exactly the kind of premature decision worth avoiding. Add a proper
 * association later if and when a real query need shows what shape it should take.
 *
 * <p>Optimistic locking via {@code @Version} exists because this project explicitly needs to
 * detect and test concurrent-update races (e.g. a reversal arriving while the original
 * transaction is still being completed) - see the plan's idempotency/reversal design.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "correlation_id", nullable = false, length = 64, updatable = false)
    private String correlationId;

    @Column(name = "mti", nullable = false, length = 4, updatable = false)
    private String mti;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private TransactionState state;

    @Column(name = "stan", nullable = false, length = 6, updatable = false)
    private String stan;

    @Column(name = "rrn", length = 12, updatable = false)
    private String rrn;

    @Column(name = "processing_code", length = 6, updatable = false)
    private String processingCode;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "currency_code", length = 3, updatable = false)
    private String currencyCode;

    @Column(name = "terminal_id", length = 8, updatable = false)
    private String terminalId;

    @Column(name = "acquiring_institution_id", length = 11, updatable = false)
    private String acquiringInstitutionId;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {
        // JPA
    }

    public static Transaction received(String correlationId, String mti, String stan, String rrn,
                                        String processingCode, Long amount, String currencyCode,
                                        String terminalId, String acquiringInstitutionId,
                                        String idempotencyKey) {
        Transaction transaction = new Transaction();
        transaction.correlationId = correlationId;
        transaction.mti = mti;
        transaction.state = TransactionState.RECEIVED;
        transaction.stan = stan;
        transaction.rrn = rrn;
        transaction.processingCode = processingCode;
        transaction.amount = amount;
        transaction.currencyCode = currencyCode;
        transaction.terminalId = terminalId;
        transaction.acquiringInstitutionId = acquiringInstitutionId;
        transaction.idempotencyKey = idempotencyKey;
        Instant now = Instant.now();
        transaction.createdAt = now;
        transaction.updatedAt = now;
        return transaction;
    }

    /**
     * Records a state change. Does not itself validate whether the transition is legal - that
     * is {@code TransactionStateMachine}'s job; this entity only records outcomes, it has no
     * opinion on transition rules.
     */
    public void applyState(TransactionState newState) {
        this.state = newState;
        this.updatedAt = Instant.now();
    }

    public void recordResponseCode(String responseCode) {
        this.responseCode = responseCode;
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public Long version() {
        return version;
    }

    public String correlationId() {
        return correlationId;
    }

    public String mti() {
        return mti;
    }

    public TransactionState state() {
        return state;
    }

    public String stan() {
        return stan;
    }

    public String rrn() {
        return rrn;
    }

    public String processingCode() {
        return processingCode;
    }

    public Long amount() {
        return amount;
    }

    public String currencyCode() {
        return currencyCode;
    }

    public String terminalId() {
        return terminalId;
    }

    public String acquiringInstitutionId() {
        return acquiringInstitutionId;
    }

    public String responseCode() {
        return responseCode;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
