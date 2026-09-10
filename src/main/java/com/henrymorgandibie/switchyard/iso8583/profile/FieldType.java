package com.henrymorgandibie.switchyard.iso8583.profile;

/**
 * The charset family a data element's value is validated against. Does not include a plain
 * ALPHA type because no data element in switchyard's profile currently needs one - add it if
 * one does, rather than carrying an untested type.
 */
public enum FieldType {
    /** Digits only. */
    NUMERIC,
    /** Printable ASCII (0x20-0x7E). */
    ANS,
    /** Track 2 charset: digits, '=', and 'D'/'d' as the field-separator variant. */
    TRACK2,
    /** Raw bytes, no charset constraint - e.g. PIN data, ICC/EMV data. */
    BINARY
}
