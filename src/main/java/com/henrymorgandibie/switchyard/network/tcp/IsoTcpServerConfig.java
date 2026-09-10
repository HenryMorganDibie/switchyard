package com.henrymorgandibie.switchyard.network.tcp;

/**
 * Configuration for {@link IsoTcpServer}.
 *
 * @param port              listening port; 0 lets the OS pick an ephemeral free port, useful in
 *                          tests - the actually-bound port is read back from {@link IsoTcpServer#boundPort()}
 * @param bossThreads       threads accepting new connections
 * @param workerThreads     threads reading/writing established connections
 * @param maxConnections    connections beyond this are accepted at the TCP level then
 *                          immediately closed - see the class-level note on {@link IsoTcpServer}
 *                          for why this is a soft, not a hard, limit
 * @param idleTimeoutSeconds a connection with no inbound traffic for this long is closed -
 *                          protects against a client that opens a connection and never sends
 *                          anything, or stops sending mid-message
 * @param maxFrameLength    the largest ISO 8583 message body switchyard will accept; see
 *                          {@code IsoFraming}
 * @param soBacklog         TCP accept backlog (pending-connection queue depth)
 */
public record IsoTcpServerConfig(
        int port,
        int bossThreads,
        int workerThreads,
        int maxConnections,
        int idleTimeoutSeconds,
        int maxFrameLength,
        int soBacklog
) {

    public static IsoTcpServerConfig defaults(int port) {
        return new IsoTcpServerConfig(port, 1, 4, 1000, 30, 4096, 128);
    }

    public IsoTcpServerConfig withMaxConnections(int newMaxConnections) {
        return new IsoTcpServerConfig(port, bossThreads, workerThreads, newMaxConnections,
                idleTimeoutSeconds, maxFrameLength, soBacklog);
    }

    public IsoTcpServerConfig withIdleTimeoutSeconds(int newIdleTimeoutSeconds) {
        return new IsoTcpServerConfig(port, bossThreads, workerThreads, maxConnections,
                newIdleTimeoutSeconds, maxFrameLength, soBacklog);
    }

    public IsoTcpServerConfig withMaxFrameLength(int newMaxFrameLength) {
        return new IsoTcpServerConfig(port, bossThreads, workerThreads, maxConnections,
                idleTimeoutSeconds, newMaxFrameLength, soBacklog);
    }
}
