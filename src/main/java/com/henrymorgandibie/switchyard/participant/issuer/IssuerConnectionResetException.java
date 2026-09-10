package com.henrymorgandibie.switchyard.participant.issuer;

/**
 * The connection to the issuer dropped mid-call. Unlike {@link IssuerUnavailableException},
 * this does not mean the transaction was necessarily unprocessed - the issuer may have already
 * committed it before the connection dropped - so the switch treats this the same way it treats
 * a timeout: outcome unknown, handle defensively.
 */
public class IssuerConnectionResetException extends RuntimeException {

    public IssuerConnectionResetException(String message) {
        super(message);
    }
}
