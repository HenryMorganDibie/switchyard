package com.henrymorgandibie.switchyard.iso8583.message;

import com.henrymorgandibie.switchyard.iso8583.exception.FieldFormatException;
import com.henrymorgandibie.switchyard.iso8583.profile.DataElementDefinition;
import com.henrymorgandibie.switchyard.iso8583.profile.FieldType;
import com.henrymorgandibie.switchyard.iso8583.profile.Iso8583Profile;
import com.henrymorgandibie.switchyard.iso8583.profile.LengthType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Immutable in-memory representation of one ISO 8583 message: an MTI plus a set of data
 * element values, each stored as its exact wire-format bytes.
 *
 * <p>{@link Builder} enforces field-level format rules (charset, exact/max length) at
 * construction time, so an {@code IsoMessage} instance is always format-valid by construction.
 * It deliberately does <strong>not</strong> enforce which fields are required for a given MTI -
 * that is a cross-field, MTI-dependent business rule, handled separately by
 * {@code RequiredFieldsValidator} in the {@code validation} package.
 *
 * <p>This class never masks, redacts, or otherwise transforms sensitive field values (PAN,
 * PIN, track data). Masking is an application-layer security concern applied at logging and
 * persistence boundaries elsewhere in switchyard, not something the codec does implicitly.
 *
 * <p>Immutable and holds no mutable shared state - safe to use concurrently across threads.
 */
public final class IsoMessage {

    private final Mti mti;
    private final SortedMap<Integer, byte[]> fields;

    private IsoMessage(Mti mti, SortedMap<Integer, byte[]> fields) {
        this.mti = mti;
        this.fields = fields;
    }

    public Mti mti() {
        return mti;
    }

    public boolean hasField(int deNumber) {
        return fields.containsKey(deNumber);
    }

    /** A defensive copy of DE{@code deNumber}'s raw wire-format bytes. */
    public byte[] rawField(int deNumber) {
        byte[] value = fields.get(deNumber);
        if (value == null) {
            throw new IllegalArgumentException("DE" + deNumber + " is not present in this message");
        }
        return Arrays.copyOf(value, value.length);
    }

    public String stringField(int deNumber) {
        return new String(rawField(deNumber), StandardCharsets.US_ASCII);
    }

    /** DE numbers present, ascending - the order fields are packed onto the wire. */
    public SortedMap<Integer, byte[]> fields() {
        return fields;
    }

    public static Builder builder(Mti mti) {
        return new Builder(mti);
    }

    public static final class Builder {

        private final Mti mti;
        private final SortedMap<Integer, byte[]> fields = new TreeMap<>();

        private Builder(Mti mti) {
            this.mti = mti;
        }

        public Builder numeric(int deNumber, String digits) {
            DataElementDefinition definition = requireType(deNumber, FieldType.NUMERIC);
            if (digits == null || digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
                throw new FieldFormatException("DE" + deNumber + " (" + definition.name()
                        + ") must be one or more digits, got: " + digits);
            }
            String wireValue = applyLength(definition, digits, true);
            fields.put(deNumber, wireValue.getBytes(StandardCharsets.US_ASCII));
            return this;
        }

        public Builder ans(int deNumber, String value) {
            DataElementDefinition definition = requireType(deNumber, FieldType.ANS);
            if (value == null || !value.chars().allMatch(c -> c >= 0x20 && c <= 0x7E)) {
                throw new FieldFormatException("DE" + deNumber + " (" + definition.name()
                        + ") must be printable ASCII, got: " + value);
            }
            String wireValue = applyLength(definition, value, false);
            fields.put(deNumber, wireValue.getBytes(StandardCharsets.US_ASCII));
            return this;
        }

        public Builder track2(int deNumber, String value) {
            DataElementDefinition definition = requireType(deNumber, FieldType.TRACK2);
            if (value == null || !value.chars().allMatch(
                    c -> Character.isDigit(c) || c == '=' || c == 'D' || c == 'd')) {
                throw new FieldFormatException("DE" + deNumber + " (" + definition.name()
                        + ") contains characters outside the track 2 charset [0-9=Dd], got: " + value);
            }
            String wireValue = applyLength(definition, value, false);
            fields.put(deNumber, wireValue.getBytes(StandardCharsets.US_ASCII));
            return this;
        }

        public Builder binary(int deNumber, byte[] value) {
            DataElementDefinition definition = requireType(deNumber, FieldType.BINARY);
            if (value == null) {
                throw new FieldFormatException("DE" + deNumber + " (" + definition.name() + ") value must not be null");
            }
            if (definition.lengthType() == LengthType.FIXED && value.length != definition.length()) {
                throw new FieldFormatException("DE" + deNumber + " (" + definition.name() + ") must be exactly "
                        + definition.length() + " bytes, got " + value.length);
            }
            if (definition.lengthType() != LengthType.FIXED
                    && (value.length == 0 || value.length > definition.length())) {
                throw new FieldFormatException("DE" + deNumber + " (" + definition.name()
                        + ") must be 1-" + definition.length() + " bytes, got " + value.length);
            }
            fields.put(deNumber, Arrays.copyOf(value, value.length));
            return this;
        }

        public IsoMessage build() {
            return new IsoMessage(mti, Collections.unmodifiableSortedMap(new TreeMap<>(fields)));
        }

        private DataElementDefinition requireType(int deNumber, FieldType expected) {
            DataElementDefinition definition = Iso8583Profile.require(deNumber);
            if (definition.type() != expected) {
                throw new FieldFormatException("DE" + deNumber + " (" + definition.name() + ") is type "
                        + definition.type() + ", not " + expected);
            }
            return definition;
        }

        /**
         * FIXED numeric fields are left-zero-padded to their declared length when shorter -
         * unambiguous, since numeric fields have a well-defined pad character. Every other
         * FIXED field type must be supplied at exactly its declared length: switchyard does not
         * guess a padding character (space vs. null) for alphanumeric fields, so callers pad
         * ans/track2 values themselves before calling the builder. LLVAR/LLLVAR fields must be
         * 1-{@code length} characters; an empty variable-length value is rejected rather than
         * silently packed as a present-but-empty field.
         */
        private String applyLength(DataElementDefinition definition, String value, boolean zeroPadNumeric) {
            return switch (definition.lengthType()) {
                case FIXED -> {
                    if (value.length() == definition.length()) {
                        yield value;
                    }
                    if (zeroPadNumeric && value.length() < definition.length()) {
                        yield "0".repeat(definition.length() - value.length()) + value;
                    }
                    throw new FieldFormatException("DE" + definition.number() + " (" + definition.name()
                            + ") must be exactly " + definition.length() + " characters, got " + value.length());
                }
                case LLVAR, LLLVAR -> {
                    if (value.isEmpty()) {
                        throw new FieldFormatException("DE" + definition.number() + " (" + definition.name()
                                + ") must not be empty");
                    }
                    if (value.length() > definition.length()) {
                        throw new FieldFormatException("DE" + definition.number() + " (" + definition.name()
                                + ") exceeds max length " + definition.length() + ", got " + value.length());
                    }
                    yield value;
                }
            };
        }
    }
}
