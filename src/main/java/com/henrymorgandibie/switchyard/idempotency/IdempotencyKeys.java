package com.henrymorgandibie.switchyard.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes a transaction's idempotency fingerprint: SHA-256 over its identifying fields
 * (acquiring institution, terminal, STAN, transmission date/time, processing code, amount).
 * Naive uniqueness on STAN alone is not safe - STAN is only 6 digits and legitimately repeats
 * across different transaction windows - so the fingerprint combines it with the fields that
 * distinguish one occurrence of a STAN from another.
 *
 * <p>This is pure, stateless key <em>computation</em> only. The actual duplicate-detection
 * <em>behavior</em> - checking Postgres/Redis before processing, handling a retried request
 * whose first attempt's response was lost, a reversal arriving after approval, a reversal
 * arriving twice - is a later milestone's {@code IdempotencyService}, not this class. This
 * exists now because the transactions table's idempotency_key column is NOT NULL and unique
 * from the schema milestone onward, so persisting any transaction at all needs a real value
 * here even before duplicate-checking exists.
 */
public final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    public static String compute(String acquiringInstitutionId, String terminalId, String stan,
                                  String transmissionDateTime, String processingCode, long amountMinorUnits) {
        String fingerprint = String.join("|",
                nullToEmpty(acquiringInstitutionId),
                nullToEmpty(terminalId),
                nullToEmpty(stan),
                nullToEmpty(transmissionDateTime),
                nullToEmpty(processingCode),
                Long.toString(amountMinorUnits));
        return sha256Hex(fingerprint);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
