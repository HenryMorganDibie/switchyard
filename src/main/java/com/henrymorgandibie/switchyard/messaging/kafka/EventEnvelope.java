package com.henrymorgandibie.switchyard.messaging.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * The JSON envelope wrapping every event this switch publishes to Kafka: a stable, self-describing
 * shape (id, type, timestamp, payload) independent of Kafka's own message metadata - a consumer
 * reading the raw value should be able to tell what it's looking at without also inspecting the
 * topic/key/headers. Plain Jackson serialization (see the publishers), not Avro or a schema
 * registry - documented in the plan as a deliberate tradeoff to avoid schema-registry scope creep
 * in a reference implementation with no real downstream consumer yet.
 */
public record EventEnvelope<T>(UUID eventId, String eventType, Instant occurredAt, T payload) {

    public static <T> EventEnvelope<T> of(String eventType, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, Instant.now(), payload);
    }
}
