package com.henrymorgandibie.switchyard.iso8583.codec;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.iso8583.profile.DataElementDefinition;
import com.henrymorgandibie.switchyard.iso8583.profile.Iso8583Profile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the response {@link IsoMessage} shell for a request: echoes the request's data
 * elements (excluding ones that only make sense on a request, like PIN data) and sets the
 * response code (DE39).
 *
 * <p>This is pure message construction - it has no knowledge of routing, issuer calls, or
 * transaction state; a transaction pipeline built on top decides things like whether to add an
 * authorization identification response (DE38) for an approval. Callers needing more than the
 * echo + response code shell built here should extend the returned message's field set via a
 * fresh {@link IsoMessage.Builder} seeded from it.
 */
public final class IsoResponseBuilder {

    /** Request-only fields that are never echoed onto the response. */
    private static final List<Integer> NEVER_ECHOED = List.of(52);

    private IsoResponseBuilder() {
    }

    public static IsoMessage buildResponse(IsoMessage request, Mti responseMti, String responseCode) {
        IsoMessage.Builder builder = IsoMessage.builder(responseMti);
        for (int de : request.fields().keySet()) {
            if (NEVER_ECHOED.contains(de)) {
                continue;
            }
            echoField(builder, de, request.rawField(de));
        }
        builder.ans(39, responseCode);
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
