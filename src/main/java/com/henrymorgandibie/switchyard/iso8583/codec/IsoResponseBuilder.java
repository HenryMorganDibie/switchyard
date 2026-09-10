package com.henrymorgandibie.switchyard.iso8583.codec;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.iso8583.profile.DataElementDefinition;
import com.henrymorgandibie.switchyard.iso8583.profile.Iso8583Profile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the response {@link IsoMessage} shell for a request: echoes the request's data
 * elements (excluding ones that only make sense on a request, like PIN data), sets the response
 * code (DE39), and optionally an authorization identification response (DE38) when the caller
 * has one (set on approval).
 *
 * <p>This is pure message construction - it has no knowledge of routing, issuer calls, or
 * transaction state; a transaction pipeline built on top decides *whether* to pass an
 * authorization id, not this class.
 */
public final class IsoResponseBuilder {

    /** Request-only fields that are never echoed onto the response. */
    private static final List<Integer> NEVER_ECHOED = List.of(52);

    private IsoResponseBuilder() {
    }

    public static IsoMessage buildResponse(IsoMessage request, Mti responseMti, String responseCode) {
        return buildResponse(request, responseMti, responseCode, null);
    }

    public static IsoMessage buildResponse(IsoMessage request, Mti responseMti, String responseCode,
                                            String authorizationId) {
        IsoMessage.Builder builder = IsoMessage.builder(responseMti);
        for (int de : request.fields().keySet()) {
            if (NEVER_ECHOED.contains(de)) {
                continue;
            }
            echoField(builder, de, request.rawField(de));
        }
        builder.ans(39, responseCode);
        if (authorizationId != null) {
            builder.ans(38, authorizationId);
        }
        return builder.build();
    }

    private static void echoField(IsoMessage.Builder builder, int de, byte[] value) {
        DataElementDefinition definition = Iso8583Profile.require(de);
        switch (definition.type()) {
            case NUMERIC -> builder.numeric(de, new String(value, StandardCharsets.US_ASCII));
            case ANS -> builder.ans(de, new String(value, StandardCharsets.US_ASCII));
            case TRACK2 -> builder.track2(de, new String(value, StandardCharsets.US_ASCII));
            case BINARY -> builder.binary(de, value);
        }
    }
}
