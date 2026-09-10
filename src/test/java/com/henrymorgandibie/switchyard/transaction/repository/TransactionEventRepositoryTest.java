package com.henrymorgandibie.switchyard.transaction.repository;

import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.domain.TransactionEvent;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "switchyard.tcp.enabled=false")
@Transactional
class TransactionEventRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionEventRepository eventRepository;

    @Test
    void eventsForATransactionAreReturnedInOccurredAtOrder() {
        Transaction transaction = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000001", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));
        UUID transactionId = transaction.id();

        eventRepository.saveAndFlush(TransactionEvent.of(transactionId, null, TransactionState.RECEIVED, "received"));
        eventRepository.saveAndFlush(TransactionEvent.of(transactionId, TransactionState.RECEIVED, TransactionState.VALIDATING, "validating"));
        eventRepository.saveAndFlush(TransactionEvent.of(transactionId, TransactionState.VALIDATING, TransactionState.VALIDATED, "validated"));

        List<TransactionEvent> events = eventRepository.findByTransactionIdOrderByOccurredAtAsc(transactionId);

        assertThat(events).hasSize(3);
        assertThat(events.get(0).fromState()).isNull();
        assertThat(events.get(0).toState()).isEqualTo(TransactionState.RECEIVED);
        assertThat(events.get(1).toState()).isEqualTo(TransactionState.VALIDATING);
        assertThat(events.get(2).toState()).isEqualTo(TransactionState.VALIDATED);
        assertThat(events).extracting(TransactionEvent::occurredAt).isSorted();
    }

    @Test
    void unrelatedTransactionsEventsAreNotMixedIn() {
        Transaction transactionA = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000002", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));
        Transaction transactionB = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000003", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));

        eventRepository.saveAndFlush(TransactionEvent.of(transactionA.id(), null, TransactionState.RECEIVED, null));
        eventRepository.saveAndFlush(TransactionEvent.of(transactionB.id(), null, TransactionState.RECEIVED, null));
        eventRepository.saveAndFlush(TransactionEvent.of(transactionB.id(), TransactionState.RECEIVED, TransactionState.VALIDATING, null));

        assertThat(eventRepository.findByTransactionIdOrderByOccurredAtAsc(transactionA.id())).hasSize(1);
        assertThat(eventRepository.findByTransactionIdOrderByOccurredAtAsc(transactionB.id())).hasSize(2);
    }
}
