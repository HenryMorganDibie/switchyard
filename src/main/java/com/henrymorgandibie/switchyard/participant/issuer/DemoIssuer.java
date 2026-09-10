package com.henrymorgandibie.switchyard.participant.issuer;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.participant.simulator.FaultInjectionRegistry;
import com.henrymorgandibie.switchyard.participant.simulator.FaultScenario;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;

import java.security.SecureRandom;

/**
 * A simulated issuer whose behavior is selected by the request's PAN (DE2) via
 * {@link FaultInjectionRegistry} - see that class for the full test-PAN table. A request with no
 * PAN, or an unrecognized one, always approves, matching this class's behavior before fault
 * injection existed.
 *
 * <p>Response codes for scenarios that do return a response are assigned here; scenarios that
 * represent the switch not getting a trustworthy response at all (unavailable, connection reset,
 * timeout) are raised as exceptions or timing behavior instead of a response value, since a
 * response code cannot represent "there was no response" - {@code TransactionProcessingPipeline}
 * is what turns those into an actual outcome and code sent back to the client.
 */
public final class DemoIssuer implements IssuerConnector {

    private static final String AUTH_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int AUTH_ID_LENGTH = 6; // matches DE38's fixed length
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Comfortably under the pipeline's default issuer-call timeout budget. */
    private static final long DELAYED_APPROVAL_SLEEP_MILLIS = 150;
    /** Comfortably over it, so the pipeline's timeout genuinely fires in tests. */
    private static final long TIMEOUT_SLEEP_MILLIS = 1500;

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
        String pan = request.hasField(2) ? request.stringField(2) : null;
        FaultScenario scenario = FaultInjectionRegistry.resolve(pan);

        return switch (scenario) {
            case APPROVE -> new IssuerResponse("00", generateAuthorizationId());
            case DECLINE -> new IssuerResponse("05", null);
            case INSUFFICIENT_FUNDS -> new IssuerResponse("51", null);
            case INVALID_ACCOUNT -> new IssuerResponse("14", null);
            case ISSUER_UNAVAILABLE -> throw new IssuerUnavailableException(
                    "DemoIssuer " + participant.code() + " refused the call (simulated)");
            case CONNECTION_RESET -> throw new IssuerConnectionResetException(
                    "connection to DemoIssuer " + participant.code() + " reset mid-call (simulated)");
            case TIMEOUT -> {
                sleep(TIMEOUT_SLEEP_MILLIS);
                yield new IssuerResponse("00", generateAuthorizationId()); // never seen - the caller gives up first
            }
            case DELAYED_APPROVAL -> {
                sleep(DELAYED_APPROVAL_SLEEP_MILLIS);
                yield new IssuerResponse("00", generateAuthorizationId());
            }
            case MALFORMED_RESPONSE -> new IssuerResponse("XX", null); // not a valid 2-digit ISO response code
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String generateAuthorizationId() {
        StringBuilder id = new StringBuilder(AUTH_ID_LENGTH);
        for (int i = 0; i < AUTH_ID_LENGTH; i++) {
            id.append(AUTH_ID_ALPHABET.charAt(RANDOM.nextInt(AUTH_ID_ALPHABET.length())));
        }
        return id.toString();
    }
}
