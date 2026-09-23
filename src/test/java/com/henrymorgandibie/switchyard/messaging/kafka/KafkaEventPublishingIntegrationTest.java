package com.henrymorgandibie.switchyard.messaging.kafka;

import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.network.management.NetworkManagementHandler;
import com.henrymorgandibie.switchyard.transaction.application.TransactionProcessingPipeline;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves messages genuinely arrive on the real, docker-compose Kafka broker - not mocked, not a
 * Spring-Kafka test abstraction, a plain {@link KafkaConsumer} constructed directly (mirroring
 * how {@code GoldenPathIntegrationTest} uses a plain {@link java.net.Socket} rather than a test
 * client) reading back what {@link TransactionEventPublisher} and {@link NetworkEventPublisher}
 * actually put on the wire. {@link TransactionEventPublisherTest}/{@link NetworkEventPublisherTest}
 * already cover the envelope/topic/key contract against a mock; this test's job is proving actual
 * end-to-end delivery.
 *
 * <p>Each consumer subscribes and seeks to the end of the topic <em>before</em> the action under
 * test runs, so it only observes messages this test itself produces - the topic is long-lived
 * (docker-compose, not Testcontainers) and may carry records from earlier runs or other tests.
 */
@SpringBootTest
@TestPropertySource(properties = "switchyard.tcp.enabled=false")
class KafkaEventPublishingIntegrationTest {

    private static final String RUN_PREFIX = String.format("%03d", System.currentTimeMillis() % 1000);

    @Autowired
    private TransactionProcessingPipeline pipeline;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private NetworkManagementHandler networkManagementHandler;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @Timeout(30)
    void anApprovedFinancialTransactionPublishesItsFullStateTransitionHistoryInOrder() throws Exception {
        String stan = RUN_PREFIX + "301";
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            subscribeFromTheEnd(consumer, KafkaTopics.TRANSACTION_EVENTS);

            byte[] request = financialRequest(stan, "0910150001", "000000005000");
            IsoMessage response = IsoMessageUnpacker.unpack(pipeline.handle("corr-kafka-1", request));
            assertThat(response.stringField(39)).isEqualTo("00");

            Transaction persisted = findByStan(stan);
            String expectedKey = persisted.id().toString();

            List<ConsumerRecord<String, String>> records = pollForRecordsWithKey(consumer, expectedKey, 6);

            List<String> toStates = new ArrayList<>();
            for (ConsumerRecord<String, String> record : records) {
                assertThat(record.key()).isEqualTo(expectedKey);
                JsonNode envelope = objectMapper.readTree(record.value());
                assertThat(envelope.get("eventType").asString()).isEqualTo("transaction.state-changed");
                toStates.add(envelope.get("payload").get("toState").asString());
            }

            assertThat(toStates).containsExactly(
                    "RECEIVED", "VALIDATING", "VALIDATED", "ROUTING", "SENT_TO_ISSUER", "APPROVED");
        }
    }

    @Test
    @Timeout(30)
    void aSignOnPublishesAnUpStatusToTheNetworkEventsTopic() throws Exception {
        String institutionId = "99" + RUN_PREFIX;
        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            subscribeFromTheEnd(consumer, KafkaTopics.NETWORK_EVENTS);

            byte[] request = networkManagementRequest("990001", "001", institutionId);
            IsoMessage response = IsoMessageUnpacker.unpack(networkManagementHandler.handle("corr-kafka-2", request));
            assertThat(response.stringField(39)).isEqualTo("00");

            List<ConsumerRecord<String, String>> records = pollForRecordsWithKey(consumer, institutionId, 1);

            JsonNode envelope = objectMapper.readTree(records.get(0).value());
            assertThat(envelope.get("eventType").asString()).isEqualTo("network.participant-status-changed");
            assertThat(envelope.get("payload").get("institutionId").asString()).isEqualTo(institutionId);
            assertThat(envelope.get("payload").get("status").asString()).isEqualTo("UP");
        }
    }

    private KafkaConsumer<String, String> newConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-event-publishing-test-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(props);
    }

    private static void subscribeFromTheEnd(KafkaConsumer<String, String> consumer, String topic) {
        consumer.subscribe(List.of(topic));
        consumer.poll(Duration.ofSeconds(5)); // forces partition assignment
        consumer.seekToEnd(consumer.assignment());
        // seekToEnd() is lazy - the client's Javadoc says it "evaluates lazily, seeking to the
        // final offset in all partitions only when polled." Left alone, that resolution happens
        // on this consumer's *next* poll() call - which, in every caller here, is after the
        // action under test has already published a message. That message would then be older
        // than the offset the lazy seek resolves to, and get silently skipped. Calling
        // position() forces the resolution to happen right now, before anything has been
        // published yet - found via a real, reproducible test failure (not a flake): the
        // producer genuinely delivered the message (verified with Kafka's own console consumer),
        // this test's own consumer was seeking past it.
        consumer.assignment().forEach(consumer::position);
    }

    private static List<ConsumerRecord<String, String>> pollForRecordsWithKey(
            KafkaConsumer<String, String> consumer, String key, int expectedCount) {
        List<ConsumerRecord<String, String>> matched = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15_000;
        while (matched.size() < expectedCount && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                if (record.key().equals(key)) {
                    matched.add(record);
                }
            });
        }
        assertThat(matched)
                .as("expected %d record(s) for key %s within the poll deadline, got %d", expectedCount, key,
                        matched.size())
                .hasSize(expectedCount);
        return matched;
    }

    private Transaction findByStan(String stan) {
        List<Transaction> matches = transactionRepository.findAll().stream()
                .filter(t -> t.stan().equals(stan))
                .toList();
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    private static byte[] financialRequest(String stan, String transmissionDateTime, String amount) {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(4, amount)
                .numeric(7, transmissionDateTime)
                .numeric(11, stan)
                .numeric(32, "12345")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
        return IsoMessagePacker.pack(message);
    }

    private static byte[] networkManagementRequest(String stan, String functionCode, String institutionId) {
        IsoMessage message = IsoMessage.builder(Mti.NETWORK_MANAGEMENT_REQUEST)
                .numeric(7, "0910150002")
                .numeric(11, stan)
                .numeric(70, functionCode)
                .numeric(32, institutionId)
                .build();
        return IsoMessagePacker.pack(message);
    }
}
