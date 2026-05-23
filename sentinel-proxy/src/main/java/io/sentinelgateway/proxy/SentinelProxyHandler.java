package io.sentinelgateway.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.sentinelgateway.core.model.HttpMethod;
import io.sentinelgateway.core.model.SentinelDecision;
import io.sentinelgateway.core.model.SentinelRequest;
import io.sentinelgateway.core.pipeline.SentinelPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Netty inbound handler: evaluates the request, then either rejects it
 * or forwards to the upstream service.
 */
@ChannelHandler.Sharable
public final class SentinelProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(SentinelProxyHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final SentinelPipeline pipeline;
    private final URI upstreamUri;

    public SentinelProxyHandler(SentinelPipeline pipeline, String upstream) {
        this.pipeline = pipeline;
        this.upstreamUri = URI.create(upstream);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        SentinelRequest sentinelReq = adapt(request);
        SentinelDecision decision = pipeline.evaluate(sentinelReq);

        if (!decision.isAllowed()) {
            sendRejection(ctx, decision);
            return;
        }

        forwardToUpstream(ctx, request);
    }

    private void sendRejection(ChannelHandlerContext ctx, SentinelDecision decision) throws Exception {
        int status = switch (decision.sentinelDecision()) {
            case BLOCK, QUARANTINE       -> 403;
            case REQUIRE_APPROVAL        -> 451;
            case ALLOW                   -> 200;
        };

        byte[] body = MAPPER.writeValueAsBytes(decision);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(status),
                Unpooled.wrappedBuffer(body)
        );
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void forwardToUpstream(ChannelHandlerContext ctx, FullHttpRequest request) {
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
         .channel(NioSocketChannel.class)
         .handler(new UpstreamRelayHandler(ctx.channel(), request.retain()));

        int port = upstreamUri.getPort() > 0 ? upstreamUri.getPort() : 80;
        b.connect(upstreamUri.getHost(), port).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.error("Failed to connect to upstream {}:{}", upstreamUri.getHost(), port);
                ctx.channel().close();
            }
        });
    }

    private SentinelRequest adapt(FullHttpRequest req) {
        String body = req.content().toString(StandardCharsets.UTF_8);
        SentinelRequest.Builder b = SentinelRequest.builder()
                .method(HttpMethod.valueOf(req.method().name()))
                .endpoint(req.uri())
                .organizationId(req.headers().get("X-Organization-Id", "default"))
                .agentId(req.headers().get("X-Agent-Id"))
                .executionThreadId(req.headers().get("X-Execution-Thread-Id"));

        req.headers().forEach(e -> b.header(e.getKey(), e.getValue()));
        if (!body.isBlank()) b.body(body);

        return b.build();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Proxy handler exception", cause);
        ctx.close();
    }
}
