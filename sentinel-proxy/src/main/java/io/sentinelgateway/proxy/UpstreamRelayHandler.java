package io.sentinelgateway.proxy;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

/**
 * Relays the approved request to upstream and pipes the response back.
 */
final class UpstreamRelayHandler extends ChannelInboundHandlerAdapter {

    private final Channel clientChannel;
    private final FullHttpRequest request;

    UpstreamRelayHandler(Channel clientChannel, FullHttpRequest request) {
        this.clientChannel = clientChannel;
        this.request = request;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(request);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        clientChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) ctx.channel().close();
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        clientChannel.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        clientChannel.close();
        ctx.close();
    }
}
