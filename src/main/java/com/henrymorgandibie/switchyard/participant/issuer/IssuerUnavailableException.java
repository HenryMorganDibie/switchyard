package com.henrymorgandibie.switchyard.participant.issuer;

/**
 * The issuer refused the call outright - a clean, known failure: the transaction was never
 * processed on the issuer's side, unlike {@link IssuerConnectionResetException} or a timeout,
 * where the outcome is genuinely unknown.
 */
public class IssuerUnavailableException extends RuntimeException {

    public IssuerUnavailableException(String message) {
        super(message);
    }
}
