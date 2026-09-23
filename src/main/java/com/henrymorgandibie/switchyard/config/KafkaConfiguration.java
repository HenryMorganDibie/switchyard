package com.henrymorgandibie.switchyard.config;

import com.henrymorgandibie.switchyard.messaging.kafka.KafkaTopics;
import com.henrymorgandibie.switchyard.messaging.kafka.NetworkEventPublisher;
import com.henrymorgandibie.switchyard.messaging.kafka.TransactionEventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provisions this switch's two Kafka topics explicitly via {@link NewTopic} beans - Spring's
 * {@code KafkaAdmin} creates them idempotently on startup if they don't already exist - rather
 * than relying on the broker's {@code auto.create.topics.enable} (on in this project's
 * {@code docker-compose.yml} as a dev convenience, but not something a real deployment should
 * depend on: auto-created topics get the broker's default partition count/replication factor,
 * not a deliberate one). Single partition, replication factor 1 - correct for this reference
 * project's single-broker docker-compose setup; a real deployment would size both for its actual
 * throughput and durability requirements.
 */
@Configuration
public class KafkaConfiguration {

    @Bean
    public NewTopic transactionEventsTopic() {
        return TopicBuilder.name(KafkaTopics.TRANSACTION_EVENTS).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic networkEventsTopic() {
        return TopicBuilder.name(KafkaTopics.NETWORK_EVENTS).partitions(1).replicas(1).build();
    }

    /**
     * One single-threaded executor per publisher for the actual work behind every publish call -
     * see {@link TransactionEventPublisher}'s Javadoc for why that work must never run on the
     * caller's own thread. Deliberately <em>not</em> {@code newVirtualThreadPerTaskExecutor()}:
     * that would run each publish call on its own independent thread with no ordering relationship
     * to any other, which would defeat the entire reason {@link KafkaTopics} publishes each topic
     * to a single partition keyed by entity id in the first place - Kafka only orders records
     * within a partition in the order the producer's {@code send()} calls actually arrive, and
     * concurrent unordered threads racing to call it provide no such guarantee. A single worker
     * thread per publisher processes its queued publish calls strictly in submission order while
     * still keeping every one of them off the request-handling thread - found via a real,
     * reproducible test failure: {@code KafkaEventPublishingIntegrationTest} caught transaction
     * events genuinely arriving out of order once publishing moved to one thread per call.
     * Backed by a virtual thread (cheap, no platform thread pinned idle waiting on I/O) rather
     * than a fixed platform thread, but there is exactly one of it per executor, not one per task.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService transactionEventPublishingExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofVirtual().name("transaction-event-publisher").factory());
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService networkEventPublishingExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofVirtual().name("network-event-publisher").factory());
    }

    @Bean
    public TransactionEventPublisher transactionEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                                 ObjectMapper objectMapper,
                                                                 ExecutorService transactionEventPublishingExecutor) {
        return new TransactionEventPublisher(kafkaTemplate, objectMapper, transactionEventPublishingExecutor);
    }

    @Bean
    public NetworkEventPublisher networkEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                         ObjectMapper objectMapper,
                                                         ExecutorService networkEventPublishingExecutor) {
        return new NetworkEventPublisher(kafkaTemplate, objectMapper, networkEventPublishingExecutor);
    }
}
