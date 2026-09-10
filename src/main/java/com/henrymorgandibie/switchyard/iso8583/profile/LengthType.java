package com.henrymorgandibie.switchyard.iso8583.profile;

/** How a data element's length is carried on the wire. */
public enum LengthType {
    /** No length prefix; always exactly {@code length} bytes. */
    FIXED,
    /** 2-digit ASCII length prefix; value is 0-{@code length} bytes. */
    LLVAR,
    /** 3-digit ASCII length prefix; value is 0-{@code length} bytes. */
    LLLVAR
}
