package com.henrymorgandibie.switchyard.reversal;

import com.henrymorgandibie.switchyard.messaging.kafka.TransactionEventPublisher;
import com.henrymorgandibie.switchyard.transaction.domain.Reversal;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.domain.TransactionEvent;
import com.henrymorgandibie.switchyard.transaction.repository.ReversalRepository;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionEventRepository;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests {@link ReversalService}'s orchestration logic against mocked repositories: which
 * original states get advanced, which are left alone, and that a real optimistic-lock conflict
 * (simulated here via a mocked exception, since a genuine one needs concurrent real transactions
 * against real Postgres) is retried against fresh state rather than failing the reversal outright.
 * {@code Transaction.id()} is null on every fixture here - Hibernate's {@code @UuidGenerator}
 * only assigns it at persist time, which these mocked-repository tests never do - so assertions
 * here go through object identity and state, not id equality. The real, DB-backed proof of the
 * race itself - two actual concurrent transactions against a real Postgres row - is
 * {@code ReversalRaceConditionIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class ReversalServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private ReversalRepository reversalRepository;

    @Mock
    private TransactionEventRepository eventRepository;

    @Mock
    private TransactionEventPublisher transactionEventPublisher;

    private ReversalService service() {
        return new ReversalService(transactionRepository, reversalRepository, eventRepository, transactionEventPublisher);
    }

    @Test
    void assignRrnIfAbsentIsANoOpWhenARrnAlreadyExists() {
        Transaction transaction = transactionWithRrn("existing-rrn");

        Transaction result = service().assignRrnIfAbsent(transaction, "0910120000");

        assertThat(result).isSameAs(transaction);
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void assignRrnIfAbsentGeneratesAndPersistsWhenMissing() {
        Transaction transaction = transactionWithRrn(null, "000123");
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = service().assignRrnIfAbsent(transaction, "0910120000");

        assertThat(result.rrn()).isEqualTo("091012000023");
        verify(transactionRepository).saveAndFlush(transaction);
    }

    @Test
    void linkToOriginalReturnsOriginalNotFoundForANullRrn() {
        ReversalLinkResult result = service().linkToOriginal(null, UUID.randomUUID(), "reason");

        assertThat(result).isInstanceOf(ReversalLinkResult.OriginalNotFound.class);
        verify(reversalRepository, never()).saveAndFlush(any());
    }

    @Test
    void linkToOriginalReturnsOriginalNotFoundWhenNoTransactionMatches() {
        when(transactionRepository.findByRrnOrderByCreatedAtDesc("RRN000000001")).thenReturn(List.of());

        ReversalLinkResult result = service().linkToOriginal("RRN000000001", UUID.randomUUID(), "reason");

        assertThat(result).isInstanceOf(ReversalLinkResult.OriginalNotFound.class);
        verify(reversalRepository, never()).saveAndFlush(any());
    }

    @Test
    void approvedOriginalIsDrivenAllTheWayToReversed() {
        Transaction original = originalInState(TransactionState.APPROVED);
        when(transactionRepository.findByRrnOrderByCreatedAtDesc("RRN000000001")).thenReturn(List.of(original));
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReversalLinkResult result = service().linkToOriginal("RRN000000001", UUID.randomUUID(), "customer dispute");

        assertThat(result).isInstanceOf(ReversalLinkResult.Linked.class);
        assertThat(original.state()).isEqualTo(TransactionState.REVERSED);

        verify(reversalRepository, times(1)).saveAndFlush(any(Reversal.class));

        ArgumentCaptor<TransactionEvent> events = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(eventRepository, times(2)).saveAndFlush(events.capture());
        assertThat(events.getAllValues().get(0).toState()).isEqualTo(TransactionState.REVERSAL_PENDING);
        assertThat(events.getAllValues().get(1).toState()).isEqualTo(TransactionState.REVERSED);
    }

    @Test
    void timeoutOriginalIsAlsoDrivenAllTheWayToReversed() {
        Transaction original = originalInState(TransactionState.TIMEOUT);
        when(transactionRepository.findByRrnOrderByCreatedAtDesc("RRN000000002")).thenReturn(List.of(original));
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReversalLinkResult result = service().linkToOriginal("RRN000000002", UUID.randomUUID(), "issuer timeout");

        assertThat(result).isInstanceOf(ReversalLinkResult.Linked.class);
        assertThat(original.state()).isEqualTo(TransactionState.REVERSED);
    }

    @Test
    void alreadyReversalPendingOriginalGoesStraightToReversed() {
        Transaction original = originalInState(TransactionState.REVERSAL_PENDING);
        when(transactionRepository.findByRrnOrderByCreatedAtDesc("RRN000000003")).thenReturn(List.of(original));
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().linkToOriginal("RRN000000003", UUID.randomUUID(), "already pending");

        assertThat(original.state()).isEqualTo(TransactionState.REVERSED);
        // Only one hop was needed, so only one transaction save and one event.
        verify(transactionRepository, times(1)).saveAndFlush(any(Transaction.class));
        verify(eventRepository, times(1)).saveAndFlush(any(TransactionEvent.class));
    }

    @Test
    void inFlightOriginalIsLinkedButItsStateIsLeftAlone() {
        Transaction original = originalInState(TransactionState.VALIDATING);
        when(transactionRepository.findByRrnOrderByCreatedAtDesc("RRN000000004")).thenReturn(List.of(original));

        ReversalLinkResult result = service().linkToOriginal("RRN000000004", UUID.randomUUID(), "premature reversal");

        assertThat(result).isInstanceOf(ReversalLinkResult.Linked.class);
        assertThat(original.state()).isEqualTo(TransactionState.VALIDATING);
        verify(reversalRepository, times(1)).saveAndFlush(any(Reversal.class));
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
        verify(eventRepository, never()).saveAndFlush(any(TransactionEvent.class));
    }

    @Test
    void alreadyTerminalOriginalIsLinkedButLeftAlone() {
        Transaction original = originalInState(TransactionState.DECLINED);
        when(transactionRepository.findByRrnOrderByCreatedAtDesc("RRN000000005")).thenReturn(List.of(original));

        service().linkToOriginal("RRN000000005", UUID.randomUUID(), "already declined");

        assertThat(original.state()).isEqualTo(TransactionState.DECLINED);
        verify(transactionRepository, never()).saveAndFlush(any(Transaction.class));
    }

    @Test
    void optimisticLockConflictIsRetriedAgainstFreshState() {
        Transaction staleOriginal = originalInState(TransactionState.APPROVED);
        Transaction freshOriginal = originalInState(TransactionState.APPROVED);

        when(transactionRepository.findByRrnOrderByCreatedAtDesc("RRN000000006")).thenReturn(List.of(staleOriginal));
        when(transactionRepository.findById(any())).thenReturn(Optional.of(freshOriginal));
        when(transactionRepository.saveAndFlush(eq(staleOriginal)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Transaction.class, "unknown"));
        when(transactionRepository.saveAndFlush(eq(freshOriginal)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReversalLinkResult result = service().linkToOriginal("RRN000000006", UUID.randomUUID(), "raced");

        assertThat(result).isInstanceOf(ReversalLinkResult.Linked.class);
        assertThat(freshOriginal.state()).isEqualTo(TransactionState.REVERSED);
        // The first hop (APPROVED -> REVERSAL_PENDING) raced and recovered onto freshOriginal;
        // the second hop (REVERSAL_PENDING -> REVERSED) then also ran against freshOriginal
        // without further contention - two legitimate saves of the same recovered instance, one
        // per hop. What matters is that staleOriginal's failed save never produced a persisted
        // row, and the Reversal audit record is written exactly once regardless of the retry -
        // retrying that insert too would produce a duplicate row.
        verify(reversalRepository, times(1)).saveAndFlush(any(Reversal.class));
        verify(transactionRepository, times(2)).saveAndFlush(eq(freshOriginal));
        verify(transactionRepository, times(1)).saveAndFlush(eq(staleOriginal));
    }

    @Test
    void mostRecentMatchIsUsedWhenMultipleTransactionsShareAnRrn() {
        Transaction mostRecent = originalInState(TransactionState.APPROVED);
        Transaction older = originalInState(TransactionState.DECLINED);
        // findByRrnOrderByCreatedAtDesc already orders newest-first (tested at the repository
        // layer) - this test only proves the service acts on the first element of that list.
        when(transactionRepository.findByRrnOrderByCreatedAtDesc("RRN000000007"))
                .thenReturn(List.of(mostRecent, older));
        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().linkToOriginal("RRN000000007", UUID.randomUUID(), "collision");

        assertThat(mostRecent.state()).isEqualTo(TransactionState.REVERSED);
        assertThat(older.state()).isEqualTo(TransactionState.DECLINED);
    }

    private static Transaction transactionWithRrn(String rrn) {
        return transactionWithRrn(rrn, "000001");
    }

    private static Transaction transactionWithRrn(String rrn, String stan) {
        return Transaction.received("corr-" + UUID.randomUUID(), "0200", stan, rrn,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID());
    }

    private static Transaction originalInState(TransactionState state) {
        Transaction transaction = Transaction.received("corr-" + UUID.randomUUID(), "0200", "000001",
                "RRN000000001", "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID());
        transaction.applyState(state);
        return transaction;
    }
}
