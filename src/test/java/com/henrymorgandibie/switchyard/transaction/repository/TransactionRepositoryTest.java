package com.henrymorgandibie.switchyard.transaction.repository;

import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.byLessThan;

/**
 * Runs against the real Postgres started via {@code docker compose up} (same pattern as
 * {@code SwitchyardApplicationTests}) - not Testcontainers, which is explicitly scoped to a
 * later milestone. Each test runs in its own Spring-managed transaction, rolled back afterward.
 * TCP gateway disabled: this test doesn't exercise the network layer.
 */
@SpringBootTest
@TestPropertySource(properties = "switchyard.tcp.enabled=false")
@Transactional
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndReloadsAllFieldsIncludingNullableOnes() {
        Transaction saved = repository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000001", "RRN000000001",
                "000000", 5000L, "566", "TERM0001", "12345",
                "idem-" + UUID.randomUUID()));
        entityManager.detach(saved);

        Transaction reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.mti()).isEqualTo("0200");
        assertThat(reloaded.state()).isEqualTo(TransactionState.RECEIVED);
        assertThat(reloaded.stan()).isEqualTo("000001");
        assertThat(reloaded.rrn()).isEqualTo("RRN000000001");
        assertThat(reloaded.processingCode()).isEqualTo("000000");
        assertThat(reloaded.amount()).isEqualTo(5000L);
        assertThat(reloaded.currencyCode()).isEqualTo("566");
        assertThat(reloaded.terminalId()).isEqualTo("TERM0001");
        assertThat(reloaded.acquiringInstitutionId()).isEqualTo("12345");
        assertThat(reloaded.responseCode()).isNull();
        assertThat(reloaded.createdAt()).isNotNull();
        assertThat(reloaded.updatedAt()).isNotNull();
    }

    @Test
    void nullableFieldsAreActuallyNullableNotJustAssumedToBe() {
        // Shaped like a network management (0800) "transaction" - no amount/currency/terminal.
        Transaction saved = repository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0800", "000004", null,
                null, null, null, null, null,
                "idem-" + UUID.randomUUID()));
        entityManager.detach(saved);

        Transaction reloaded = repository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.rrn()).isNull();
        assertThat(reloaded.processingCode()).isNull();
        assertThat(reloaded.amount()).isNull();
        assertThat(reloaded.currencyCode()).isNull();
        assertThat(reloaded.terminalId()).isNull();
        assertThat(reloaded.acquiringInstitutionId()).isNull();
    }

    @Test
    void findByIdempotencyKeyLocatesTheTransaction() {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        repository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000002", null,
                "000000", 1000L, "566", "TERM0001", "12345", idempotencyKey));

        assertThat(repository.findByIdempotencyKey(idempotencyKey)).isPresent();
        assertThat(repository.findByIdempotencyKey("does-not-exist")).isEmpty();
    }

    @Test
    void findByCorrelationIdLocatesTheTransaction() {
        String correlationId = "corr-" + UUID.randomUUID();
        repository.saveAndFlush(Transaction.received(
                correlationId, "0200", "000003", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));

        assertThat(repository.findByCorrelationId(correlationId)).isPresent();
    }

    @Test
    void duplicateIdempotencyKeyIsRejectedByTheDatabaseUniqueConstraint() {
        String idempotencyKey = "idem-" + UUID.randomUUID();
        repository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000005", null,
                "000000", 1000L, "566", "TERM0001", "12345", idempotencyKey));

        Transaction duplicate = Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000006", null,
                "000000", 2000L, "566", "TERM0001", "12345", idempotencyKey);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentUpdatesToTheSameTransactionAreDetectedViaOptimisticLocking() {
        Transaction original = repository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000007", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));
        entityManager.detach(original);

        Transaction copyA = repository.findById(original.id()).orElseThrow();
        entityManager.detach(copyA);
        Transaction copyB = repository.findById(original.id()).orElseThrow();
        entityManager.detach(copyB);

        copyA.applyState(TransactionState.VALIDATING);
        repository.saveAndFlush(copyA);

        copyB.applyState(TransactionState.FAILED);
        assertThatThrownBy(() -> repository.saveAndFlush(copyB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void applyStateUpdatesStateAndUpdatedAt() {
        Transaction saved = repository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000008", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));
        var createdAt = saved.createdAt();
        var firstUpdatedAt = saved.updatedAt();

        saved.applyState(TransactionState.APPROVED);
        saved.recordResponseCode("00");
        repository.saveAndFlush(saved);
        entityManager.detach(saved);

        Transaction reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.state()).isEqualTo(TransactionState.APPROVED);
        assertThat(reloaded.responseCode()).isEqualTo("00");
        // Postgres timestamptz stores microsecond precision; Instant.now() can carry
        // nanoseconds, so an exact comparison after a DB round trip is the wrong check here.
        assertThat(reloaded.createdAt()).isCloseTo(createdAt, byLessThan(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(reloaded.updatedAt()).isAfterOrEqualTo(firstUpdatedAt);
    }
}
