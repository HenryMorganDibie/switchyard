package com.henrymorgandibie.switchyard.iso8583.validation;

import com.henrymorgandibie.switchyard.iso8583.exception.RequiredFieldMissingException;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;

import java.util.Map;
import java.util.Set;

/**
 * Validates that an {@link IsoMessage} carries the data elements switchyard requires for its
 * MTI. This is a business-level, cross-field rule and is deliberately not enforced by
 * {@link IsoMessage.Builder} itself, which only enforces per-field format: a message can be
 * well-formed (every present field valid) and still be missing a field its MTI requires, so the
 * two checks are kept as separate pipeline stages.
 */
public final class RequiredFieldsValidator {

    // DE32 (acquiring institution) is required on the three routable request MTIs - found while
    // building the transaction pipeline: without it, TransactionRouter has nothing to resolve an
    // acquirer from, and every such transaction would fail routing regardless of anything else
    // about it. It was originally left optional; real usage revealed it isn't.
    private static final Map<Mti, Set<Integer>> REQUIRED = Map.of(
            Mti.AUTHORIZATION_REQUEST, Set.of(3, 4, 7, 11, 32, 41, 49),
            Mti.AUTHORIZATION_RESPONSE, Set.of(3, 4, 7, 11, 39, 41, 49),
            Mti.FINANCIAL_REQUEST, Set.of(3, 4, 7, 11, 32, 41, 49),
            Mti.FINANCIAL_RESPONSE, Set.of(3, 4, 7, 11, 39, 41, 49),
            Mti.REVERSAL_REQUEST, Set.of(3, 4, 7, 11, 32, 37, 41, 49),
            Mti.REVERSAL_RESPONSE, Set.of(3, 4, 7, 11, 37, 39, 41, 49),
            Mti.NETWORK_MANAGEMENT_REQUEST, Set.of(7, 11),
            Mti.NETWORK_MANAGEMENT_RESPONSE, Set.of(7, 11, 39)
    );

    private RequiredFieldsValidator() {
    }

    public static void validate(IsoMessage message) {
        for (int de : REQUIRED.getOrDefault(message.mti(), Set.of())) {
            if (!message.hasField(de)) {
                throw new RequiredFieldMissingException(message.mti().code(), de);
            }
        }
    }
}
