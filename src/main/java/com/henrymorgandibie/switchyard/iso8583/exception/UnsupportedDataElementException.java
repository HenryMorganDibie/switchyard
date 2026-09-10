package com.henrymorgandibie.switchyard.iso8583.exception;

/** The bitmap references a data element number that switchyard's profile does not define. */
public class UnsupportedDataElementException extends IsoMessageException {

    public UnsupportedDataElementException(int deNumber) {
        super("DE" + deNumber + " is not defined in the switchyard profile");
    }
}
