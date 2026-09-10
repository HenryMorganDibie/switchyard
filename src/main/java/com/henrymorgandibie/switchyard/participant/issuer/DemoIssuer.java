package com.henrymorgandibie.switchyard.participant.issuer;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;

import java.security.SecureRandom;

/**
 * A simulated issuer that always approves. Deliberately approve-only for this milestone -
 * configurable fault injection (decline, insufficient funds, timeout, ...) extends this in a
 * later milestone; it is not a rewrite of it.
 */
public final class DemoIssuer implements IssuerConnector {

    private static final String AUTH_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int AUTH_ID_LENGTH = 6; // matches DE38's fixed length
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NetworkParticipant participant;

    public DemoIssuer(NetworkParticipant participant) {
        if (participant.role() != NetworkParticipant.Role.ISSUER) {
            throw new IllegalArgumentException(
                    "DemoIssuer requires an ISSUER participant, got " + participant.role());
        }
        this.participant = participant;
    }

    @Override
    public NetworkParticipant participant() {
        return participant;
    }

    @Override
    public IssuerResponse authorize(IsoMessage request) {
        return new IssuerResponse("00", generateAuthorizationId());
    }

    private static String generateAuthorizationId() {
        StringBuilder id = new StringBuilder(AUTH_ID_LENGTH);
        for (int i = 0; i < AUTH_ID_LENGTH; i++) {
            id.append(AUTH_ID_ALPHABET.charAt(RANDOM.nextInt(AUTH_ID_ALPHABET.length())));
        }
        return id.toString();
    }
}
