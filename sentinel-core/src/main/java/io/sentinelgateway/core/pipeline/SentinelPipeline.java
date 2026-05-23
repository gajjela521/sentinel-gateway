package io.sentinelgateway.core.pipeline;

import io.sentinelgateway.core.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chains all registered {@link SentinelLayer} implementations in order.
 * Short-circuits on the first terminal decision. Emits an {@link AuditEvent}
 * for every request via the configured sink regardless of outcome.
 *
 * Virtual-thread safe: all mutable state lives in per-call locals or
 * thread-confined {@link PipelineContext} instances.
 */
public final class SentinelPipeline {

    private static final Logger log = LoggerFactory.getLogger(SentinelPipeline.class);

    private final List<SentinelLayer> layers;
    private final Consumer<AuditEvent> auditSink;

    public SentinelPipeline(List<SentinelLayer> layers, Consumer<AuditEvent> auditSink) {
        if (layers == null || layers.isEmpty()) throw new IllegalArgumentException("At least one layer required");
        this.layers = List.copyOf(layers);
        this.auditSink = auditSink != null ? auditSink : e -> {};
    }

    public SentinelDecision evaluate(SentinelRequest request) {
        String traceId = UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        PipelineContext ctx = new PipelineContext();

        LayerDecision terminal = null;
        String decidingLayer = null;

        for (SentinelLayer layer : layers) {
            LayerDecision decision;
            try {
                decision = layer.evaluate(request, ctx);
            } catch (Exception ex) {
                log.error("Layer {} threw unexpected exception for request {}", layer.name(), request.requestId(), ex);
                decision = LayerDecision.block(
                        "layer-exception",
                        "Internal error in layer " + layer.name(),
                        "Contact your security administrator",
                        null, 1.0
                );
            }

            if (decision.isTerminal()) {
                terminal = decision;
                decidingLayer = layer.name();
                break;
            }
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        SentinelDecision result = buildDecision(terminal, traceId, ctx);
        auditSink.accept(buildAudit(request, result, decidingLayer,
                terminal != null ? terminal.ruleId() : null,
                terminal != null ? terminal.reason() : null,
                traceId, elapsedNanos, ctx));

        return result;
    }

    private SentinelDecision buildDecision(LayerDecision terminal, String traceId, PipelineContext ctx) {
        if (terminal == null) return SentinelDecision.allow(traceId);

        SentinelDecision.Builder b = switch (terminal.outcome()) {
            case BLOCK            -> SentinelDecision.block();
            case QUARANTINE       -> SentinelDecision.quarantine();
            case REQUIRE_APPROVAL -> SentinelDecision.requireApproval();
            case PASS             -> throw new IllegalStateException("unreachable");
        };

        Double drift   = ctx.get(PipelineContext.KEY_DRIFT_SCORE);
        Double inject  = ctx.get(PipelineContext.KEY_INJECTION_SCORE);
        Double schema  = ctx.get(PipelineContext.KEY_SCHEMA_ANOMALY);
        Double mutatW  = ctx.get(PipelineContext.KEY_MUTATION_WEIGHT);

        return b.ruleId(terminal.ruleId())
                .reason(terminal.reason())
                .agentGuidance(terminal.agentGuidance())
                .safeAlternatives(terminal.safeAlternatives())
                .auditTraceId(traceId)
                .scores(new SentinelDecision.ScoreDetail(
                        drift   != null ? drift   : 0.0,
                        inject  != null ? inject  : 0.0,
                        schema  != null ? schema  : 0.0,
                        mutatW  != null ? mutatW  : 0.0
                ))
                .build();
    }

    private AuditEvent buildAudit(SentinelRequest req, SentinelDecision decision,
                                   String decidingLayer, String ruleId, String reason,
                                   String traceId, long nanos, PipelineContext ctx) {
        Double drift  = ctx.get(PipelineContext.KEY_DRIFT_SCORE);
        Double inject = ctx.get(PipelineContext.KEY_INJECTION_SCORE);
        Double schema = ctx.get(PipelineContext.KEY_SCHEMA_ANOMALY);

        return new AuditEvent(
                traceId, req.requestId(), req.agentId(), req.organizationId(),
                req.executionThreadId(), req.method(), req.endpoint(),
                decision.sentinelDecision(), decidingLayer, ruleId, reason,
                Instant.now(), nanos,
                new AuditEvent.ScoreSnapshot(
                        drift  != null ? drift  : 0.0,
                        inject != null ? inject : 0.0,
                        schema != null ? schema : 0.0,
                        0.0, 0
                )
        );
    }
}
