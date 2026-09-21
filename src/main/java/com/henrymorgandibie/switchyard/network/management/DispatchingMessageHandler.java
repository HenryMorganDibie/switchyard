package com.henrymorgandibie.switchyard.network.management;

import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.network.tcp.IsoMessageHandler;

import java.nio.charset.StandardCharsets;

/**
 * The TCP gateway's single entry point: routes a decoded message body to
 * {@link NetworkManagementHandler} for 0800 (network management), or to the transaction pipeline
 * for everything else - the only two kinds of traffic this switch understands.
 *
 * <p>Routing is decided by peeking at just the first 4 bytes (the MTI is always the wire format's
 * first field - see {@code IsoMessagePacker}/{@code IsoMessageUnpacker}) rather than fully
 * unpacking the message here: both downstream handlers already do their own full unpack, and
 * duplicating that work on every message for a routing decision that only needs 4 bytes would be
 * wasteful. A body too short to even contain an MTI is forwarded to the transaction pipeline
 * unchanged - its own unpack step is what produces the real, detailed malformed-message error;
 * this dispatcher doesn't need a second opinion on what's wrong with it.
 */
public final class DispatchingMessageHandler implements IsoMessageHandler {

    private static final int MTI_LENGTH = 4;

    private final IsoMessageHandler transactionHandler;
    private final IsoMessageHandler networkManagementHandler;

    public DispatchingMessageHandler(IsoMessageHandler transactionHandler, IsoMessageHandler networkManagementHandler) {
        this.transactionHandler = transactionHandler;
        this.networkManagementHandler = networkManagementHandler;
    }

    @Override
    public byte[] handle(String correlationId, byte[] requestBody) {
        if (isNetworkManagementRequest(requestBody)) {
            return networkManagementHandler.handle(correlationId, requestBody);
        }
        return transactionHandler.handle(correlationId, requestBody);
    }

    private static boolean isNetworkManagementRequest(byte[] requestBody) {
        if (requestBody.length < MTI_LENGTH) {
            return false;
        }
        String mtiCode = new String(requestBody, 0, MTI_LENGTH, StandardCharsets.US_ASCII);
        return Mti.NETWORK_MANAGEMENT_REQUEST.code().equals(mtiCode);
    }
}
