package com.henrymorgandibie.switchyard.network.tcp;

import com.henrymorgandibie.switchyard.network.framing.IsoFraming;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Wires each newly-accepted channel's pipeline: frame decoder/encoder, an idle-connection
 * timeout, a soft connection-count limit, then the per-message handler.
 *
 * <p>The connection limit is enforced by checking {@code channelGroup.size()} against
 * {@code maxConnections} before adding the new channel, and closing the channel immediately if
 * already at capacity. This is a soft limit: the size check and the add are not atomic, so a
 * burst of connections arriving at exactly the same instant could briefly exceed
 * {@code maxConnections} by a small margin. That tradeoff is accepted here in exchange for not
 * needing a lock on the hot accept path; a production deployment fronting the gateway with a
 * connection-aware load balancer would enforce this more strictly upstream.
 */
final class IsoServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger log = LoggerFactory.getLogger(IsoServerChannelInitializer.class);

    private final IsoTcpServerConfig config;
    private final IsoMessageHandler messageHandler;
    private final ChannelGroup channelGroup;

    IsoServerChannelInitializer(IsoTcpServerConfig config, IsoMessageHandler messageHandler, ChannelGroup channelGroup) {
        this.config = config;
        this.messageHandler = messageHandler;
        this.channelGroup = channelGroup;
    }

    @Override
    protected void initChannel(SocketChannel channel) {
        if (channelGroup.size() >= config.maxConnections()) {
            log.warn("remote={} rejected: at max connections ({})", channel.remoteAddress(), config.maxConnections());
            channel.close();
            return;
        }
        channelGroup.add(channel);

        channel.pipeline()
                .addLast("frameDecoder", IsoFraming.newFrameDecoder(config.maxFrameLength()))
                .addLast("frameEncoder", IsoFraming.newFramePrepender())
                .addLast("idleState", new IdleStateHandler(config.idleTimeoutSeconds(), 0, 0, TimeUnit.SECONDS))
                .addLast("isoHandler", new IsoServerInboundHandler(messageHandler));
    }
}
