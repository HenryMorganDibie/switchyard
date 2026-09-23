package com.henrymorgandibie.switchyard.messaging.kafka;

import com.henrymorgandibie.switchyard.transaction.domain.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

/**
 * Publishes every {@link TransactionEvent} this switch records to {@link KafkaTopics#TRANSACTION_EVENTS},
 * keyed by transaction id so Kafka preserves per-transaction ordering (see {@link KafkaTopics}'s
 * Javadoc).
 *
 * <p>Best-effort, at-most-once delivery from this switch's side: a publish failure is logged, not
 * thrown. The transaction's own {@code TransactionEvent} row - already committed to Postgres
 * before this is ever called - is the durable source of truth for what happened; Kafka here is a
 * downstream notification channel, not part of the transaction's own correctness guarantee. There
 * is no outbox pattern, no retry, and no redelivery - a message genuinely lost during a Kafka
 * outage is gone, not replayed later. A production deployment that needs guaranteed delivery would
 * need a transactional outbox (writing the event and an "to be published" marker in the same DB
 * transaction, with a separate relay process); that is out of scope for this milestone and
 * documented as a known limitation, not hidden.
 *
 * <p>{@code publish} always hands the actual work to {@code executor} and returns immediately,
 * rather than doing it on the caller's thread. This matters beyond just avoiding a blocked event
 * loop: {@code KafkaProducer.send()} can itself block the calling thread for up to
 * {@code max.block.ms} while it fetches topic metadata - bounded (see {@code application.yml}),
 * but a single transaction produces several events in sequence (RECEIVED, VALIDATING, ...,
 * APPROVED), and each one calling {@code publish} synchronously against an unreachable broker
 * would compound that bound across every event in the request, not just one - found via
 * {@code GoldenPathIntegrationTest} genuinely timing out against a deliberately-unreachable
 * Kafka broker before this was fixed. Offloading to {@code executor} makes the bound per-message,
 * not per-request, and keeps it off the request-handling thread entirely either way.
 *
 * <p>{@code executor} must run its tasks strictly in submission order (a single worker thread,
 * not one thread per task) - see {@code KafkaConfiguration}'s Javadoc for why an unordered
 * executor here would silently break the same ordering guarantee this class exists to provide.
 */
public final class TransactionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventPublisher.class);
    private static final String EVENT_TYPE = "transaction.state-changed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public TransactionEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                      ExecutorService executor) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public void publish(TransactionEvent event) {
        executor.submit(() -> doPublish(event));
    }

    private void doPublish(TransactionEvent event) {
        String key = event.transactionId().toString();
        TransactionEventPayload payload = new TransactionEventPayload(event.transactionId(),
                event.fromState() == null ? null : event.fromState().name(), event.toState().name(), event.detail());
        EventEnvelope<TransactionEventPayload> envelope = EventEnvelope.of(EVENT_TYPE, payload);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(KafkaTopics.TRANSACTION_EVENTS, key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("failed to publish transaction event {} for transaction {}", envelope.eventId(), key, ex);
                }
            });
        } catch (Exception e) {
            log.warn("failed to serialize/publish transaction event for transaction {}", key, e);
        }
    }
}
