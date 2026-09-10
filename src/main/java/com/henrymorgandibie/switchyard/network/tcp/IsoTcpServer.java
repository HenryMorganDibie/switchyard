package com.henrymorgandibie.switchyard.network.tcp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * switchyard's ISO 8583 TCP gateway: a Netty server that accepts connections, reconstructs
 * length-prefixed messages (see {@code IsoFraming}), and dispatches each one to a configured
 * {@link IsoMessageHandler}.
 *
 * <p>This class owns the full connection lifecycle: accepting, per-connection idle timeout,
 * a soft connection-count limit (see {@link IsoServerChannelInitializer}), and graceful
 * shutdown (closes tracked connections, then the listening socket, then the event loop groups,
 * all with bounded timeouts so {@link #stop()} is a synchronous call a caller can rely on
 * completing rather than something that might hang).
 *
 * <p>Uses NIO transport ({@link NioServerSocketChannel}) rather than a platform-specific
 * transport (e.g. epoll) for portability across the development/target environments this
 * project runs on.
 *
 * <p>Not thread-safe for concurrent {@link #start()}/{@link #stop()} calls - callers are
 * expected to manage one server's lifecycle from a single thread (Spring's lifecycle callbacks,
 * or a test), which is the normal usage pattern for a server component.
 */
public final class IsoTcpServer {

    private static final Logger log = LoggerFactory.getLogger(IsoTcpServer.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;

    private final IsoTcpServerConfig config;
    private final IsoMessageHandler messageHandler;
    private final ChannelGroup channelGroup = new DefaultChannelGroup("switchyard-tcp-connections", GlobalEventExecutor.INSTANCE);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public IsoTcpServer(IsoTcpServerConfig config, IsoMessageHandler messageHandler) {
        this.config = config;
        this.messageHandler = messageHandler;
    }

    /** Starts listening. Blocks until the listening socket is bound. */
    public void start() {
        if (serverChannel != null) {
            throw new IllegalStateException("server already started");
        }

        bossGroup = new MultiThreadIoEventLoopGroup(config.bossThreads(), NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(config.workerThreads(), NioIoHandler.newFactory());

        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, config.soBacklog())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new IsoServerChannelInitializer(config, messageHandler, channelGroup));

            serverChannel = bootstrap.bind(config.port()).sync().channel();
            log.info("switchyard TCP gateway listening on port {}", boundPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownEventLoopGroups();
            throw new IllegalStateException("interrupted while starting TCP gateway", e);
        } catch (RuntimeException e) {
            shutdownEventLoopGroups();
            throw e;
        }
    }

    /**
     * Stops listening and closes all connections, waiting (bounded) for each stage to complete.
     * Safe to call even if {@link #start()} was never called or already failed.
     */
    public void stop() {
        try {
            channelGroup.close().await(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (serverChannel != null) {
                serverChannel.close().await(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            shutdownEventLoopGroups();
            serverChannel = null;
            log.info("switchyard TCP gateway stopped");
        }
    }

    private void shutdownEventLoopGroups() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS).syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    /** The actually-bound port; resolves an ephemeral port (configured as 0) to the real one. */
    public int boundPort() {
        if (serverChannel == null) {
            throw new IllegalStateException("server is not started");
        }
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public int activeConnectionCount() {
        return channelGroup.size();
    }
}
