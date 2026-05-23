package io.sentinelgateway.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.sentinelgateway.core.pipeline.SentinelPipeline;
import io.sentinelgateway.core.pipeline.SentinelPipelineBuilder;
import io.sentinelgateway.core.policy.PolicyLoader;
import io.sentinelgateway.core.policy.PolicyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Standalone proxy mode entry point.
 * Usage: java -jar sentinel-proxy.jar --port=8080 --upstream=http://api.example.com --policy=./policies.yaml
 *
 * Runs as a Netty HTTP server. Every inbound request is evaluated by the
 * Sentinel pipeline before being forwarded to the upstream service.
 */
public final class ProxyMain {

    private static final Logger log = LoggerFactory.getLogger(ProxyMain.class);

    public static void main(String[] args) throws Exception {
        ProxyConfig config = ProxyConfig.fromArgs(args);
        log.info("Starting Sentinel Proxy on port {} → upstream {}", config.port(), config.upstream());

        SentinelPipeline pipeline = buildPipeline(config);

        NioEventLoopGroup bossGroup   = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new HttpServerCodec());
                     p.addLast(new HttpObjectAggregator(10 * 1024 * 1024)); // 10 MB max
                     p.addLast(new SentinelProxyHandler(pipeline, config.upstream()));
                 }
             });

            Channel ch = b.bind(config.port()).sync().channel();
            log.info("Sentinel Proxy listening on port {}", config.port());
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private static SentinelPipeline buildPipeline(ProxyConfig config) throws Exception {
        SentinelPipelineBuilder builder = new SentinelPipelineBuilder()
                .injectionScanner(true)
                .semanticDrift(false)
                .financialCircuitBreaker(true);

        if (config.policyFile() != null) {
            try (InputStream is = new FileInputStream(config.policyFile())) {
                List<PolicyRule> rules = PolicyLoader.fromYaml(is);
                builder.policyRules(rules);
            }
        }

        return builder.build();
    }
}
