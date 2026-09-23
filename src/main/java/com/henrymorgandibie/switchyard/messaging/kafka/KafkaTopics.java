package com.henrymorgandibie.switchyard.messaging.kafka;

/**
 * The two topics this switch publishes to. Each is keyed so that everything about one entity
 * (one transaction, one network participant) lands on the same partition and is therefore
 * strictly ordered relative to itself - Kafka only guarantees ordering within a partition, not
 * across an entire topic, so the key choice is what makes "this transaction's events arrive in
 * the order they happened" a real guarantee rather than a hope.
 */
public final class KafkaTopics {

    /** Key = transaction id. Every {@code TransactionEvent} this switch records is published here. */
    public static final String TRANSACTION_EVENTS = "transaction-events";

    /** Key = institution id (the DE32 value on the wire - see NetworkManagementHandler's Javadoc). */
    public static final String NETWORK_EVENTS = "network-events";

    private KafkaTopics() {
    }
}
