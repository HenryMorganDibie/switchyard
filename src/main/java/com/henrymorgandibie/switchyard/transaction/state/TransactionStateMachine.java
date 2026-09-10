package com.henrymorgandibie.switchyard.transaction.state;

import com.henrymorgandibie.switchyard.transaction.domain.Transaction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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

/**
 * The explicit transition table for {@link TransactionState}: which state changes are legal.
 * Deliberately not a general-purpose state machine framework - a plain {@code Map<State,
 * Set<State>>} is the whole mechanism, fully transparent and independently testable without any
 * framework machinery to explain away.
 *
 * <p>{@code TIMEOUT -> REVERSAL_PENDING} is legal because a real switch cannot tell, from a
 * timeout alone, whether the issuer actually processed the transaction before going silent -
 * the safe assumption is that it might have, so a reversal is attempted defensively rather than
 * simply marking the transaction FAILED and moving on.
 *
 * <p>Stateless and thread-safe: the transition table is built once into an immutable map.
 */
public final class TransactionStateMachine {

    private static final Map<TransactionState, Set<TransactionState>> TRANSITIONS = buildTransitions();

    private TransactionStateMachine() {
    }

    public static boolean canTransition(TransactionState from, TransactionState to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTerminal(TransactionState state) {
        return TRANSITIONS.getOrDefault(state, Set.of()).isEmpty();
    }

    /**
     * Validates and applies the transition on {@code transaction}, or throws
     * {@link IllegalStateTransitionException} and leaves the transaction's state unchanged.
     */
    public static void transition(Transaction transaction, TransactionState to) {
        TransactionState from = transaction.state();
        if (!canTransition(from, to)) {
            throw new IllegalStateTransitionException(from, to);
        }
        transaction.applyState(to);
    }

    private static Map<TransactionState, Set<TransactionState>> buildTransitions() {
        Map<TransactionState, Set<TransactionState>> transitions = new EnumMap<>(TransactionState.class);
        transitions.put(RECEIVED, EnumSet.of(VALIDATING));
        transitions.put(VALIDATING, EnumSet.of(VALIDATED, FAILED));
        transitions.put(VALIDATED, EnumSet.of(ROUTING));
        transitions.put(ROUTING, EnumSet.of(SENT_TO_ISSUER, FAILED));
        transitions.put(SENT_TO_ISSUER, EnumSet.of(APPROVED, DECLINED, TIMEOUT, FAILED));
        transitions.put(APPROVED, EnumSet.of(REVERSAL_PENDING));
        transitions.put(TIMEOUT, EnumSet.of(REVERSAL_PENDING));
        transitions.put(REVERSAL_PENDING, EnumSet.of(REVERSED, FAILED));
        transitions.put(DECLINED, Set.of());
        transitions.put(REVERSED, Set.of());
        transitions.put(FAILED, Set.of());
        return Collections.unmodifiableMap(transitions);
    }
}
