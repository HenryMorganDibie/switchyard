package com.henrymorgandibie.switchyard.network.tcp.support;

import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoResponseBuilder;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.network.tcp.IsoMessageHandler;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Test-only {@link IsoMessageHandler} used by the TCP gateway's network-layer tests. Uses the
 * real ISO 8583 codec (from the codec milestone) to unpack the request and build a genuine
 * approval response, so the TCP tests exercise real message parsing - not a raw byte echo - while
 * staying deliberately free of the transaction pipeline (no state machine, routing, persistence,
 * or Kafka): that wiring is a later milestone's job, not the TCP gateway's.
 *
 * <p>Also records every correlation id it was called with, so tests can assert the gateway
 * generates a distinct id per message.
 */
public final class EchoResponseHandler implements IsoMessageHandler {

    private static final Map<Mti, Mti> RESPONSE_MTI = new EnumMap<>(Mti.class);

    static {
        RESPONSE_MTI.put(Mti.AUTHORIZATION_REQUEST, Mti.AUTHORIZATION_RESPONSE);
        RESPONSE_MTI.put(Mti.FINANCIAL_REQUEST, Mti.FINANCIAL_RESPONSE);
        RESPONSE_MTI.put(Mti.REVERSAL_REQUEST, Mti.REVERSAL_RESPONSE);
        RESPONSE_MTI.put(Mti.NETWORK_MANAGEMENT_REQUEST, Mti.NETWORK_MANAGEMENT_RESPONSE);
    }

    private final ConcurrentLinkedQueue<String> correlationIdsSeen = new ConcurrentLinkedQueue<>();

    @Override
    public byte[] handle(String correlationId, byte[] requestBody) {
        correlationIdsSeen.add(correlationId);
        IsoMessage request = IsoMessageUnpacker.unpack(requestBody);
        Mti responseMti = RESPONSE_MTI.get(request.mti());
        IsoMessage response = IsoResponseBuilder.buildResponse(request, responseMti, "00");
        return IsoMessagePacker.pack(response);
    }

    public ConcurrentLinkedQueue<String> correlationIdsSeen() {
        return correlationIdsSeen;
    }
}
