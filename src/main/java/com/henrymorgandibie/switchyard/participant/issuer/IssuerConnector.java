package com.henrymorgandibie.switchyard.participant.issuer;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;

/**
 * How the switch addresses a simulated issuer to get an authorization decision. Implementations
 * receive the full request message rather than individual fields, since which fields matter
 * (amount, processing code, PAN) is the issuer's concern, not the router's or the caller's.
 */
public interface IssuerConnector {

    NetworkParticipant participant();

    IssuerResponse authorize(IsoMessage request);
}
