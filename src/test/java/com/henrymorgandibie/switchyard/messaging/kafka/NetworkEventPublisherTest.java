package com.henrymorgandibie.switchyard.messaging.kafka;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
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
 * Unit tests {@link NetworkEventPublisher}'s own contract against a mocked {@link KafkaTemplate}
 * - see {@link TransactionEventPublisherTest}'s Javadoc for why this doesn't need a real broker,
 * and for why a same-thread executor is used here instead of the real async one.
 */
@ExtendWith(MockitoExtension.class)
class NetworkEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService sameThreadExecutor = sameThreadExecutor();

    @Test
    void publishesToTheNetworkEventsTopicKeyedByInstitutionId() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        NetworkEventPublisher publisher = new NetworkEventPublisher(kafkaTemplate, objectMapper, sameThreadExecutor);

        publisher.publish("12345", "UP");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(KafkaTopics.NETWORK_EVENTS);
        assertThat(keyCaptor.getValue()).isEqualTo("12345");

        JsonNode envelope = objectMapper.readTree(valueCaptor.getValue());
        assertThat(envelope.get("eventType").asString()).isEqualTo("network.participant-status-changed");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("institutionId").asString()).isEqualTo("12345");
        assertThat(payload.get("status").asString()).isEqualTo("UP");
    }

    @Test
    void aKafkaTemplateExceptionDuringSendDoesNotPropagate() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("broker unreachable"));
        NetworkEventPublisher publisher = new NetworkEventPublisher(kafkaTemplate, objectMapper, sameThreadExecutor);

        assertThatCode(() -> publisher.publish("12345", "DOWN")).doesNotThrowAnyException();
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
