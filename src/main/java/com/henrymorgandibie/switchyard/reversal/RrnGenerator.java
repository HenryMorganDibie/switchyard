package com.henrymorgandibie.switchyard.reversal;

/**
 * Assigns a Retrieval Reference Number (ISO 8583 DE37, an12) to a transaction that didn't
 * already carry one - real ISO 8583 practice is for the acquirer/switch to generate the RRN,
 * not the terminal, so a switch that only ever echoed a client-supplied DE37 would leave clients
 * with no reliable value to reference in a later reversal.
 *
 * <p>Deterministically derived from the transmission date/time (DE7, 10 digits) and the last 2
 * digits of the STAN (DE11), concatenated to exactly 12 characters - DE37's fixed length. This
 * is simple and needs no additional state (a counter, a sequence) to stay correct, at the cost
 * of a small, accepted collision risk: two different transactions with identical DE7 down to the
 * second <em>and</em> a matching STAN suffix would generate the same RRN. {@code ReversalService}
 * resolves that rare case by preferring the most recently created match rather than treating it
 * as an error.
 */
public final class RrnGenerator {

    private RrnGenerator() {
    }

    public static String generate(String transmissionDateTime, String stan) {
        if (transmissionDateTime == null || transmissionDateTime.length() != 10) {
            throw new IllegalArgumentException(
                    "transmissionDateTime must be 10 digits (DE7 format), got: " + transmissionDateTime);
        }
        if (stan == null || stan.length() < 2) {
            throw new IllegalArgumentException("stan must be at least 2 characters, got: " + stan);
        }
        return transmissionDateTime + stan.substring(stan.length() - 2);
    }
}
