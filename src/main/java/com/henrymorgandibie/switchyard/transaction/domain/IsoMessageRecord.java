package com.henrymorgandibie.switchyard.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A captured wire-level ISO 8583 message associated with a transaction, kept only in sanitized
 * form: {@code sanitizedPayload} is a field-number-to-value map with PAN/PIN/track data masked,
 * never the raw wire bytes. Masking itself is applied by whatever writes this record (a later,
 * security-focused milestone) - this entity only defines the shape it is stored in.
 */
@Entity
@Table(name = "iso_messages")
public class IsoMessageRecord {

    public enum Direction {
        INBOUND, OUTBOUND
    }

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 8, updatable = false)
    private Direction direction;

    @Column(name = "mti", nullable = false, length = 4, updatable = false)
    private String mti;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sanitized_payload", nullable = false, updatable = false)
    private Map<String, String> sanitizedPayload;

    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    protected IsoMessageRecord() {
        // JPA
    }

    public static IsoMessageRecord of(UUID transactionId, Direction direction, String mti,
                                       Map<String, String> sanitizedPayload) {
        IsoMessageRecord record = new IsoMessageRecord();
        record.transactionId = transactionId;
        record.direction = direction;
        record.mti = mti;
        record.sanitizedPayload = sanitizedPayload;
        record.capturedAt = Instant.now();
        return record;
    }

    public UUID id() {
        return id;
    }

    public UUID transactionId() {
        return transactionId;
    }

    public Direction direction() {
        return direction;
    }

    public String mti() {
        return mti;
    }

    public Map<String, String> sanitizedPayload() {
        return sanitizedPayload;
    }

    public Instant capturedAt() {
        return capturedAt;
    }
}
