package com.henrymorgandibie.switchyard.iso8583.exception;

/** A data element value does not satisfy its profile's type, length, or charset rules. */
public class FieldFormatException extends IsoMessageException {

    public FieldFormatException(String message) {
        super(message);
    }
}
