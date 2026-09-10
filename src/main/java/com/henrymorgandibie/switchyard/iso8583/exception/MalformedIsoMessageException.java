package com.henrymorgandibie.switchyard.iso8583.exception;

/**
 * The wire bytes being unpacked do not form a valid switchyard ISO 8583 message: truncated,
 * contain an invalid length prefix, an invalid bitmap, or trailing garbage.
 */
public class MalformedIsoMessageException extends IsoMessageException {

    public MalformedIsoMessageException(String message) {
        super(message);
    }
}
