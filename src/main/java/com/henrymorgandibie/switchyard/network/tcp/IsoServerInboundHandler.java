package com.henrymorgandibie.switchyard.network.tcp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Per-connection Netty handler: for each fully-framed inbound message, generates a correlation
 * id, hands the raw body to the configured {@link IsoMessageHandler}, and writes the response
 * back framed the same way. Closes the connection on any error or on idle timeout rather than
 * letting a single bad message or a silent client take down the whole server - one connection's
 * problem does not affect any other connection.
 *
 * <p>Not shareable: a new instance is created per channel by {@link IsoServerChannelInitializer},
 * matching Netty's own per-connection handler lifecycle.
 */
final class IsoServerInboundHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(IsoServerInboundHandler.class);

    private final IsoMessageHandler messageHandler;

    IsoServerInboundHandler(IsoMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        String correlationId = UUID.randomUUID().toString();
        byte[] requestBody = new byte[frame.readableBytes()];
        frame.readBytes(requestBody);

        log.debug("correlationId={} remote={} received {} byte message",
                correlationId, ctx.channel().remoteAddress(), requestBody.length);

        byte[] responseBody;
        try {
            responseBody = messageHandler.handle(correlationId, requestBody);
        } catch (Exception e) {
            log.warn("correlationId={} remote={} message handler failed, closing connection: {}",
                    correlationId, ctx.channel().remoteAddress(), e.getMessage());
            ctx.close();
            return;
        }

        ByteBuf responseFrame = Unpooled.wrappedBuffer(responseBody);
        ctx.writeAndFlush(responseFrame);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            log.debug("remote={} idle timeout, closing connection", ctx.channel().remoteAddress());
            ctx.close();
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("remote={} channel error, closing connection: {}", ctx.channel().remoteAddress(), cause.toString());
        ctx.close();
    }
}
