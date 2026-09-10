package com.henrymorgandibie.switchyard.iso8583.exception;

/** A message is missing a data element that switchyard requires for its MTI. */
public class RequiredFieldMissingException extends IsoMessageException {

    public RequiredFieldMissingException(String mti, int deNumber) {
        super("MTI " + mti + " is missing required DE" + deNumber);
    }
}
