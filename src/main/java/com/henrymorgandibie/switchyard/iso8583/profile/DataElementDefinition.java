package com.henrymorgandibie.switchyard.iso8583.profile;

/**
 * One data element's wire-format definition: its type, how its length is carried, and either
 * its fixed length or its LLVAR/LLLVAR maximum length.
 */
public record DataElementDefinition(
        int number,
        String name,
        FieldType type,
        LengthType lengthType,
        int length
) {

    public DataElementDefinition {
        if (number < 2 || number > 128) {
            throw new IllegalArgumentException("DE number must be in [2,128]: " + number);
        }
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive for DE" + number);
        }
        if (lengthType == LengthType.LLVAR && length > 99) {
            throw new IllegalArgumentException("LLVAR max length cannot exceed 99 for DE" + number);
        }
        if (lengthType == LengthType.LLLVAR && length > 999) {
            throw new IllegalArgumentException("LLLVAR max length cannot exceed 999 for DE" + number);
        }
    }

    /** Number of ASCII digits used for this data element's wire length prefix (0 for FIXED). */
    public int lengthPrefixDigits() {
        return switch (lengthType) {
            case FIXED -> 0;
            case LLVAR -> 2;
            case LLLVAR -> 3;
        };
    }
}
