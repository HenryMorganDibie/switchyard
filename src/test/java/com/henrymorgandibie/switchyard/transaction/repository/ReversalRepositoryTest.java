package com.henrymorgandibie.switchyard.transaction.repository;

import com.henrymorgandibie.switchyard.transaction.domain.Reversal;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import jakarta.persistence.EntityManager;
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
class ReversalRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ReversalRepository reversalRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void reversalCanBeRecordedBeforeTheReversalTransactionExists() {
        Transaction original = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000001", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));

        Reversal saved = reversalRepository.saveAndFlush(
                Reversal.of(original.id(), null, "customer dispute"));
        entityManager.detach(saved);

        Reversal reloaded = reversalRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.originalTransactionId()).isEqualTo(original.id());
        assertThat(reloaded.reversalTransactionId()).isNull();
        assertThat(reloaded.reason()).isEqualTo("customer dispute");
    }

    @Test
    void linkingTheReversalTransactionPersists() {
        Transaction original = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000002", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));
        Transaction reversalTransaction = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0400", "000003", "RRN000000001",
                "020000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));

        Reversal saved = reversalRepository.saveAndFlush(Reversal.of(original.id(), null, "timeout"));
        saved.linkReversalTransaction(reversalTransaction.id());
        reversalRepository.saveAndFlush(saved);
        entityManager.detach(saved);

        Reversal reloaded = reversalRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.reversalTransactionId()).isEqualTo(reversalTransaction.id());
    }

    @Test
    void findByOriginalTransactionIdReturnsOnlyThatTransactionsReversals() {
        Transaction transactionA = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000004", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));
        Transaction transactionB = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000005", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));

        reversalRepository.saveAndFlush(Reversal.of(transactionA.id(), null, "reason A"));

        List<Reversal> forA = reversalRepository.findByOriginalTransactionId(transactionA.id());
        List<Reversal> forB = reversalRepository.findByOriginalTransactionId(transactionB.id());

        assertThat(forA).hasSize(1);
        assertThat(forB).isEmpty();
    }
}
