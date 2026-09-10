package com.henrymorgandibie.switchyard.iso8583.exception;

/** Base type for all ISO 8583 message construction/parsing errors. */
public class IsoMessageException extends RuntimeException {

    public IsoMessageException(String message) {
        super(message);
    }

    public IsoMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
