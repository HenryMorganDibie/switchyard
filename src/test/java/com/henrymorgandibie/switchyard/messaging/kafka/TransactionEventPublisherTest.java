package com.henrymorgandibie.switchyard.messaging.kafka;

import com.henrymorgandibie.switchyard.transaction.domain.TransactionEvent;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests {@link TransactionEventPublisher}'s own contract - correct topic, key, and JSON
 * envelope shape - against a mocked {@link KafkaTemplate}, and that it never lets a publish
 * failure escape as an exception. A real, unmocked-broker proof that a message actually arrives
 * on the topic is {@code KafkaEventPublishingIntegrationTest}.
 *
 * <p>Uses a same-thread {@code ExecutorService} (runs the submitted task immediately, inline)
 * rather than the real virtual-thread executor the switch wires up - {@code publish} is genuinely
 * asynchronous in production (see the class Javadoc), but making these assertions synchronous
 * keeps this test deterministic without needing to poll for a background task to finish.
 */
@ExtendWith(MockitoExtension.class)
class TransactionEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService sameThreadExecutor = sameThreadExecutor();

    @Test
    void publishesToTheTransactionEventsTopicKeyedByTransactionId() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        TransactionEventPublisher publisher =
                new TransactionEventPublisher(kafkaTemplate, objectMapper, sameThreadExecutor);
        UUID transactionId = UUID.randomUUID();
        TransactionEvent event = TransactionEvent.of(transactionId, TransactionState.VALIDATING,
                TransactionState.VALIDATED, "validation passed");

        publisher.publish(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopics.TRANSACTION_EVENTS);
        assertThat(keyCaptor.getValue()).isEqualTo(transactionId.toString());

        JsonNode envelope = objectMapper.readTree(valueCaptor.getValue());
        assertThat(envelope.get("eventType").asString()).isEqualTo("transaction.state-changed");
        assertThat(envelope.get("eventId").asString()).isNotBlank();
        assertThat(envelope.get("occurredAt").asString()).isNotBlank();
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("transactionId").asString()).isEqualTo(transactionId.toString());
        assertThat(payload.get("fromState").asString()).isEqualTo("VALIDATING");
        assertThat(payload.get("toState").asString()).isEqualTo("VALIDATED");
        assertThat(payload.get("detail").asString()).isEqualTo("validation passed");
    }

    @Test
    void aNullFromStateIsRepresentedAsAbsentInThePayload() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        TransactionEventPublisher publisher =
                new TransactionEventPublisher(kafkaTemplate, objectMapper, sameThreadExecutor);
        UUID transactionId = UUID.randomUUID();
        TransactionEvent event = TransactionEvent.of(transactionId, null, TransactionState.RECEIVED,
                "message unpacked and validated");

        publisher.publish(event);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), valueCaptor.capture());
        JsonNode payload = objectMapper.readTree(valueCaptor.getValue()).get("payload");
        assertThat(payload.get("fromState").isNull()).isTrue();
    }

    @Test
    void aKafkaTemplateExceptionDuringSendDoesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("broker unreachable"));
        TransactionEventPublisher publisher =
                new TransactionEventPublisher(kafkaTemplate, objectMapper, sameThreadExecutor);
        TransactionEvent event = TransactionEvent.of(UUID.randomUUID(), null, TransactionState.RECEIVED, "detail");

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }

    @Test
    void aFailedSendFutureIsLoggedNotThrown() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("simulated broker timeout"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);
        TransactionEventPublisher publisher =
                new TransactionEventPublisher(kafkaTemplate, objectMapper, sameThreadExecutor);
        TransactionEvent event = TransactionEvent.of(UUID.randomUUID(), null, TransactionState.RECEIVED, "detail");

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
    }

    private static ExecutorService sameThreadExecutor() {
        return new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
            }

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };
    }
}
