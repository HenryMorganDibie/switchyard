package com.henrymorgandibie.switchyard.network.tcp;

/**
 * Handles one decoded, complete ISO 8583 message body and returns the response body to send
 * back over the same connection. Implementations receive raw bytes (already de-framed by
 * {@code IsoFraming}) - packing/unpacking is the implementation's concern, not the gateway's.
 *
 * <p>The gateway calls this synchronously on a Netty event-loop thread. An implementation that
 * does blocking work (database calls, downstream network calls) must offload it to another
 * executor itself rather than blocking the event loop - the gateway does not do this on the
 * implementation's behalf, since how to offload is a pipeline-level decision made in a later
 * milestone once there is a real transaction pipeline to offload work from.
 */
@FunctionalInterface
public interface IsoMessageHandler {

    byte[] handle(String correlationId, byte[] requestBody);
}
