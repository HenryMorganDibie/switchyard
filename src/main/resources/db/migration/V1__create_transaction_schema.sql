-- Core transaction domain schema. Scoped to this migration: transactions, transaction_events,
-- iso_messages, reversals - the tables backing the transaction/domain entities. Tables for
-- routing (network_participants, terminals, merchants, routing_rules) and admin audit
-- (audit_events) are deliberately NOT created here; they belong to the milestones that
-- actually introduce that functionality (routing, and the admin API respectively), each adding
-- its own migration when it needs one.

CREATE TABLE transactions (
    id                          UUID PRIMARY KEY,
    version                     BIGINT NOT NULL DEFAULT 0,
    correlation_id              VARCHAR(64) NOT NULL,
    mti                         VARCHAR(4)  NOT NULL,
    state                       VARCHAR(32) NOT NULL,
    stan                        VARCHAR(6)  NOT NULL,
    rrn                         VARCHAR(12),
    processing_code             VARCHAR(6),
    amount                      BIGINT,
    currency_code               VARCHAR(3),
    terminal_id                 VARCHAR(8),
    acquiring_institution_id    VARCHAR(11),
    response_code               VARCHAR(2),
    idempotency_key             VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Enforces the idempotency guarantee at the database level - this is the actual mechanism that
-- prevents duplicate processing (see docs/idempotency.md once written), not merely an
-- optimization; the Redis fast-path cache planned for a later milestone sits in front of this.
CREATE UNIQUE INDEX ux_transactions_idempotency_key ON transactions (idempotency_key);

-- Looking a transaction up by the correlation id generated at the TCP gateway (tracing one
-- network exchange back to its persisted transaction) is a normal admin/debugging access path.
CREATE INDEX ix_transactions_correlation_id ON transactions (correlation_id);

-- Admin queries filter by state routinely (e.g. "show me everything stuck in SENT_TO_ISSUER").
CREATE INDEX ix_transactions_state ON transactions (state);

CREATE TABLE transaction_events (
    id              UUID PRIMARY KEY,
    transaction_id  UUID NOT NULL REFERENCES transactions (id),
    from_state      VARCHAR(32),
    to_state        VARCHAR(32) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    detail          TEXT
);

-- Postgres does not automatically index foreign key columns; fetching one transaction's full
-- event history (its audit trail) is the table's primary access pattern.
CREATE INDEX ix_transaction_events_transaction_id ON transaction_events (transaction_id);

CREATE TABLE iso_messages (
    id                  UUID PRIMARY KEY,
    transaction_id      UUID NOT NULL REFERENCES transactions (id),
    direction           VARCHAR(8) NOT NULL,
    mti                 VARCHAR(4) NOT NULL,
    -- A sanitized (PAN/PIN/track-masked) field map, never the raw wire bytes - see
    -- IsoMessageRecord's Javadoc. Masking itself is applied by a later milestone; this column's
    -- shape is what that milestone writes into.
    sanitized_payload   JSONB NOT NULL,
    captured_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_iso_messages_transaction_id ON iso_messages (transaction_id);

CREATE TABLE reversals (
    id                          UUID PRIMARY KEY,
    original_transaction_id     UUID NOT NULL REFERENCES transactions (id),
    reversal_transaction_id     UUID REFERENCES transactions (id),
    reason                      VARCHAR(255) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_reversals_original_transaction_id ON reversals (original_transaction_id);
