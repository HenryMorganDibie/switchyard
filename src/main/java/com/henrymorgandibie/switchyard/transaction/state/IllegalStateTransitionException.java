package com.henrymorgandibie.switchyard.transaction.state;

/** A transition between two {@link TransactionState} values that the transition table forbids. */
public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(TransactionState from, TransactionState to) {
        super("Cannot transition from " + from + " to " + to);
    }
}
