package com.henrymorgandibie.switchyard.messaging.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

/**
 * Publishes network participant status changes (sign-on/sign-off, see
 * {@code NetworkManagementHandler}) to {@link KafkaTopics#NETWORK_EVENTS}, keyed by institution
 * id. Same best-effort, at-most-once delivery tradeoff, and same reason for offloading to
 * {@code executor} rather than publishing on the caller's thread, as
 * {@link TransactionEventPublisher} - see its Javadoc.
 */
public final class NetworkEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NetworkEventPublisher.class);
    private static final String EVENT_TYPE = "network.participant-status-changed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public NetworkEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                  ExecutorService executor) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public void publish(String institutionId, String status) {
        executor.submit(() -> doPublish(institutionId, status));
    }

    private void doPublish(String institutionId, String status) {
        EventEnvelope<NetworkEventPayload> envelope =
                EventEnvelope.of(EVENT_TYPE, new NetworkEventPayload(institutionId, status));
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(KafkaTopics.NETWORK_EVENTS, institutionId, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("failed to publish network event {} for institution {}", envelope.eventId(),
                            institutionId, ex);
                }
            });
        } catch (Exception e) {
            log.warn("failed to serialize/publish network event for institution {}", institutionId, e);
        }
    }
}
