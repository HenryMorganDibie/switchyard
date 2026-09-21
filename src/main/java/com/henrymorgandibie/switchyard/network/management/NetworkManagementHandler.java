package com.henrymorgandibie.switchyard.network.management;

import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoResponseBuilder;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.validation.MtiValidator;
import com.henrymorgandibie.switchyard.iso8583.validation.RequiredFieldsValidator;
import com.henrymorgandibie.switchyard.network.tcp.IsoMessageHandler;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipantStatusRegistry;

import java.util.function.Consumer;

/**
 * Handles network management (0800/0810) sign-on, sign-off, and echo - connection-level
 * housekeeping between the switch and a participant, distinct from an actual financial
 * transaction. Deliberately does not touch the {@code transactions} table or its audit trail at
 * all: a sign-on isn't a transaction in the sense the rest of this project models one (see
 * {@code TransactionProcessingPipeline}'s Javadoc, which explicitly scopes 0800/0810 out), it's
 * network-layer state that {@link NetworkParticipantStatusRegistry} tracks instead.
 *
 * <p>DE70 (Network Management Information Code) selects the function, using switchyard's own
 * project-defined convention (see {@code docs/iso8583.md}) rather than any one real network's
 * exact code table, consistent with the rest of this profile:
 * <ul>
 *   <li>{@code 001} sign-on - requires DE32 (here read as the signing-on institution's id, not
 *       {@code NetworkParticipant.code()} - see {@link NetworkParticipantStatusRegistry}'s
 *       Javadoc for why those are different identifier namespaces in this codebase); marks that
 *       institution {@link com.henrymorgandibie.switchyard.routing.domain.ParticipantStatus#UP}.</li>
 *   <li>{@code 002} sign-off - requires DE32; marks that institution
 *       {@link com.henrymorgandibie.switchyard.routing.domain.ParticipantStatus#DOWN}.</li>
 *   <li>{@code 301} echo test - a bare connectivity/keepalive check, DE32 optional and ignored;
 *       never changes any institution's tracked status.</li>
 * </ul>
 * DE32 is only conditionally required (by function code), not universally required for every
 * 0800 - real ISO 8583 profiles commonly have such cross-field conditional rules, and this is
 * intentionally modeled rather than simplified away: a sign-on/sign-off with no way to tell which
 * institution it's for is a real, distinct error case (response code {@code 30}, format error)
 * from an unrecognized function code entirely (response code {@code 12}, invalid transaction).
 */
public final class NetworkManagementHandler implements IsoMessageHandler {

    private static final String FUNCTION_SIGN_ON = "001";
    private static final String FUNCTION_SIGN_OFF = "002";
    private static final String FUNCTION_ECHO_TEST = "301";

    private static final String RESPONSE_CODE_APPROVED = "00";
    private static final String RESPONSE_CODE_FORMAT_ERROR = "30";
    private static final String RESPONSE_CODE_INVALID_TRANSACTION = "12";

    private final NetworkParticipantStatusRegistry statusRegistry;

    public NetworkManagementHandler(NetworkParticipantStatusRegistry statusRegistry) {
        this.statusRegistry = statusRegistry;
    }

    @Override
    public byte[] handle(String correlationId, byte[] requestBody) {
        IsoMessage request = IsoMessageUnpacker.unpack(requestBody);
        MtiValidator.validate(request.mti().code());
        RequiredFieldsValidator.validate(request);

        String functionCode = request.stringField(70);
        String institutionId = request.hasField(32) ? request.stringField(32) : null;

        String responseCode = switch (functionCode) {
            case FUNCTION_SIGN_ON -> applyIfInstitutionKnown(institutionId, statusRegistry::markUp);
            case FUNCTION_SIGN_OFF -> applyIfInstitutionKnown(institutionId, statusRegistry::markDown);
            case FUNCTION_ECHO_TEST -> RESPONSE_CODE_APPROVED;
            default -> RESPONSE_CODE_INVALID_TRANSACTION;
        };

        IsoMessage response = IsoResponseBuilder.buildResponse(request, request.mti().responseMti(), responseCode);
        return IsoMessagePacker.pack(response);
    }

    private String applyIfInstitutionKnown(String institutionId, Consumer<String> action) {
        if (institutionId == null) {
            return RESPONSE_CODE_FORMAT_ERROR;
        }
        action.accept(institutionId);
        return RESPONSE_CODE_APPROVED;
    }
}
