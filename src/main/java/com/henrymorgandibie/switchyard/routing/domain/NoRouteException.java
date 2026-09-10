package com.henrymorgandibie.switchyard.routing.domain;

/**
 * No issuer could be resolved for a transaction: the acquiring institution is unrecognized, has
 * no active routing rule, or its routing rule points at an issuer that isn't registered. Maps
 * to the ROUTING -&gt; FAILED transition in the transaction state machine.
 */
public class NoRouteException extends RuntimeException {

    public NoRouteException(String message) {
        super(message);
    }
}
