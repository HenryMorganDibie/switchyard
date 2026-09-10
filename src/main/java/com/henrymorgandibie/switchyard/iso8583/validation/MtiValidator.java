package com.henrymorgandibie.switchyard.iso8583.validation;

import com.henrymorgandibie.switchyard.iso8583.exception.UnsupportedMtiException;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;

/**
 * The transaction pipeline's explicit "validate MTI" stage: a single, named entry point for
 * confirming a wire-level MTI code is one switchyard supports, distinct from the parsing that
 * already happens inside {@code IsoMessageUnpacker}.
 */
public final class MtiValidator {

    private MtiValidator() {
    }

    /** Returns the matching {@link Mti}, or throws {@link UnsupportedMtiException}. */
    public static Mti validate(String code) {
        return Mti.fromCode(code);
    }
}
