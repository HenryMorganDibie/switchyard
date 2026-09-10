package com.henrymorgandibie.switchyard.transaction.state;

import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.APPROVED;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.DECLINED;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.FAILED;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.RECEIVED;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.REVERSAL_PENDING;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.REVERSED;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.ROUTING;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.SENT_TO_ISSUER;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.TIMEOUT;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.VALIDATED;
import static com.henrymorgandibie.switchyard.transaction.state.TransactionState.VALIDATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionStateMachineTest {

    /**
     * Independently written from the plan's transition diagram - not derived by calling
     * {@link TransactionStateMachine} - so the exhaustive test below is a real check against an
     * external specification, not the implementation agreeing with itself.
     */
    private static final Set<Pair> LEGAL_TRANSITIONS = Set.of(
            new Pair(RECEIVED, VALIDATING),
            new Pair(VALIDATING, VALIDATED),
            new Pair(VALIDATING, FAILED),
            new Pair(VALIDATED, ROUTING),
            new Pair(ROUTING, SENT_TO_ISSUER),
            new Pair(ROUTING, FAILED),
            new Pair(SENT_TO_ISSUER, APPROVED),
            new Pair(SENT_TO_ISSUER, DECLINED),
            new Pair(SENT_TO_ISSUER, TIMEOUT),
            new Pair(SENT_TO_ISSUER, FAILED),
            new Pair(APPROVED, REVERSAL_PENDING),
            new Pair(TIMEOUT, REVERSAL_PENDING),
            new Pair(REVERSAL_PENDING, REVERSED),
            new Pair(REVERSAL_PENDING, FAILED)
    );

    private static final Set<TransactionState> TERMINAL_STATES = EnumSet.of(DECLINED, REVERSED, FAILED);

    @Test
    void everyOneOfTheOneHundredTwentyOneStatePairsMatchesTheHandWrittenTransitionTable() {
        int checked = 0;
        for (TransactionState from : TransactionState.values()) {
            for (TransactionState to : TransactionState.values()) {
                boolean expectedLegal = LEGAL_TRANSITIONS.contains(new Pair(from, to));
                boolean actualLegal = TransactionStateMachine.canTransition(from, to);
                assertThat(actualLegal)
                        .as("%s -> %s should be %s", from, to, expectedLegal ? "legal" : "illegal")
                        .isEqualTo(expectedLegal);
                checked++;
            }
        }
        assertThat(checked).isEqualTo(TransactionState.values().length * TransactionState.values().length);
    }

    @Test
    void exactlyDeclinedReversedAndFailedAreTerminal() {
        for (TransactionState state : TransactionState.values()) {
            assertThat(TransactionStateMachine.isTerminal(state))
                    .as("%s terminal?", state)
                    .isEqualTo(TERMINAL_STATES.contains(state));
        }
    }

    @Test
    void validTransitionAppliesTheNewStateAndUpdatesTimestamp() {
        Transaction transaction = sampleTransaction();
        var before = transaction.updatedAt();

        TransactionStateMachine.transition(transaction, VALIDATING);

        assertThat(transaction.state()).isEqualTo(VALIDATING);
        assertThat(transaction.updatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void invalidTransitionThrowsAndLeavesStateUnchanged() {
        Transaction transaction = sampleTransaction(); // RECEIVED

        assertThatThrownBy(() -> TransactionStateMachine.transition(transaction, APPROVED))
                .isInstanceOf(IllegalStateTransitionException.class)
                .hasMessageContaining("RECEIVED")
                .hasMessageContaining("APPROVED");

        assertThat(transaction.state()).isEqualTo(RECEIVED);
    }

    @Test
    void transitioningAwayFromATerminalStateIsRejected() {
        Transaction transaction = sampleTransaction();
        TransactionStateMachine.transition(transaction, VALIDATING);
        TransactionStateMachine.transition(transaction, VALIDATED);
        TransactionStateMachine.transition(transaction, ROUTING);
        TransactionStateMachine.transition(transaction, SENT_TO_ISSUER);
        TransactionStateMachine.transition(transaction, DECLINED);

        assertThatThrownBy(() -> TransactionStateMachine.transition(transaction, VALIDATING))
                .isInstanceOf(IllegalStateTransitionException.class);
        assertThat(transaction.state()).isEqualTo(DECLINED);
    }

    @Test
    void fullApprovalAndReversalPathReachesReversed() {
        Transaction transaction = sampleTransaction();

        TransactionStateMachine.transition(transaction, VALIDATING);
        TransactionStateMachine.transition(transaction, VALIDATED);
        TransactionStateMachine.transition(transaction, ROUTING);
        TransactionStateMachine.transition(transaction, SENT_TO_ISSUER);
        TransactionStateMachine.transition(transaction, APPROVED);
        TransactionStateMachine.transition(transaction, REVERSAL_PENDING);
        TransactionStateMachine.transition(transaction, REVERSED);

        assertThat(transaction.state()).isEqualTo(REVERSED);
    }

    @Test
    void timeoutPathDefensivelyAttemptsReversalThenCanStillFail() {
        Transaction transaction = sampleTransaction();

        TransactionStateMachine.transition(transaction, VALIDATING);
        TransactionStateMachine.transition(transaction, VALIDATED);
        TransactionStateMachine.transition(transaction, ROUTING);
        TransactionStateMachine.transition(transaction, SENT_TO_ISSUER);
        TransactionStateMachine.transition(transaction, TIMEOUT);
        TransactionStateMachine.transition(transaction, REVERSAL_PENDING);
        TransactionStateMachine.transition(transaction, FAILED);

        assertThat(transaction.state()).isEqualTo(FAILED);
    }

    private static Transaction sampleTransaction() {
        return Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000001", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID());
    }

    private record Pair(TransactionState from, TransactionState to) {
    }
}
