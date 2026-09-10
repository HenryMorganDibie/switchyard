package com.henrymorgandibie.switchyard.iso8583.profile;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * switchyard's data element profile: a project-defined ISO 8583:1987-inspired profile. This is
 * <strong>not</strong> a reproduction of any proprietary card-scheme, network, or NIBSS
 * specification - see {@code docs/iso8583.md} for the full rationale and field-by-field
 * documentation.
 *
 * <p>DE70 (Network Management Information Code) was added beyond the field list originally
 * scoped for the codec milestone: it is the standard field real switches use to distinguish
 * sign-on/sign-off/echo within 0800/0810 (needed for Milestone B), and including it here lets
 * the codec's bitmap handling be exercised end-to-end with a genuine secondary-bitmap field
 * (DE65-128) rather than a synthetic placeholder - every other data element in this profile
 * is numbered 55 or below.
 */
public final class Iso8583Profile {

    private static final Map<Integer, DataElementDefinition> DEFINITIONS = buildDefinitions();

    private Iso8583Profile() {
    }

    public static DataElementDefinition require(int deNumber) {
        DataElementDefinition definition = DEFINITIONS.get(deNumber);
        if (definition == null) {
            throw new IllegalArgumentException("DE" + deNumber + " is not defined in the switchyard profile");
        }
        return definition;
    }

    public static boolean isDefined(int deNumber) {
        return DEFINITIONS.containsKey(deNumber);
    }

    public static Map<Integer, DataElementDefinition> all() {
        return DEFINITIONS;
    }

    private static Map<Integer, DataElementDefinition> buildDefinitions() {
        Map<Integer, DataElementDefinition> definitions = new TreeMap<>();
        add(definitions, 2, "Primary Account Number", FieldType.NUMERIC, LengthType.LLVAR, 19);
        add(definitions, 3, "Processing Code", FieldType.NUMERIC, LengthType.FIXED, 6);
        add(definitions, 4, "Transaction Amount", FieldType.NUMERIC, LengthType.FIXED, 12);
        add(definitions, 7, "Transmission Date and Time", FieldType.NUMERIC, LengthType.FIXED, 10);
        add(definitions, 11, "System Trace Audit Number", FieldType.NUMERIC, LengthType.FIXED, 6);
        add(definitions, 12, "Local Transaction Time", FieldType.NUMERIC, LengthType.FIXED, 6);
        add(definitions, 13, "Local Transaction Date", FieldType.NUMERIC, LengthType.FIXED, 4);
        add(definitions, 18, "Merchant Type", FieldType.NUMERIC, LengthType.FIXED, 4);
        add(definitions, 22, "POS Entry Mode", FieldType.NUMERIC, LengthType.FIXED, 3);
        add(definitions, 25, "POS Condition Code", FieldType.NUMERIC, LengthType.FIXED, 2);
        add(definitions, 32, "Acquiring Institution ID", FieldType.NUMERIC, LengthType.LLVAR, 11);
        add(definitions, 35, "Track 2 Data", FieldType.TRACK2, LengthType.LLVAR, 37);
        add(definitions, 37, "Retrieval Reference Number", FieldType.ANS, LengthType.FIXED, 12);
        add(definitions, 38, "Authorization Identification Response", FieldType.ANS, LengthType.FIXED, 6);
        add(definitions, 39, "Response Code", FieldType.ANS, LengthType.FIXED, 2);
        add(definitions, 41, "Card Acceptor Terminal ID", FieldType.ANS, LengthType.FIXED, 8);
        add(definitions, 42, "Card Acceptor ID Code", FieldType.ANS, LengthType.FIXED, 15);
        add(definitions, 43, "Card Acceptor Name/Location", FieldType.ANS, LengthType.FIXED, 40);
        add(definitions, 49, "Currency Code, Transaction", FieldType.NUMERIC, LengthType.FIXED, 3);
        add(definitions, 52, "PIN Data", FieldType.BINARY, LengthType.FIXED, 8);
        add(definitions, 55, "ICC / EMV Data", FieldType.BINARY, LengthType.LLLVAR, 255);
        add(definitions, 70, "Network Management Information Code", FieldType.NUMERIC, LengthType.FIXED, 3);
        return Collections.unmodifiableMap(definitions);
    }

    private static void add(Map<Integer, DataElementDefinition> definitions, int number, String name,
                             FieldType type, LengthType lengthType, int length) {
        definitions.put(number, new DataElementDefinition(number, name, type, lengthType, length));
    }
}
