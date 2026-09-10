package com.henrymorgandibie.switchyard.iso8583.exception;

/** The MTI is not one of switchyard's eight supported MTIs. */
public class UnsupportedMtiException extends IsoMessageException {

    public UnsupportedMtiException(String mti) {
        super("Unsupported MTI: " + mti);
    }
}
