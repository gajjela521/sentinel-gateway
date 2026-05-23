package io.sentinelgateway.core.injection;

import io.sentinelgateway.core.model.LayerDecision;
import io.sentinelgateway.core.model.SentinelRequest;
import io.sentinelgateway.core.pipeline.PipelineContext;
import io.sentinelgateway.core.pipeline.SentinelLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Layer 2: Prompt Injection Scanner.
 *
 * <p>Scans the request body against a library of named injection patterns.
 * The aggregate score is the <em>maximum</em> severity across all matching patterns
 * (not the sum) to avoid penalising benign text that coincidentally activates multiple
 * weak signals.
 *
 * <ul>
 *   <li>Score {@code >= blockThreshold} → {@link LayerDecision.Outcome#BLOCK}
 *   <li>Score {@code >= flagThreshold}  → passes through with the score recorded in context
 *   <li>Null or blank body             → score 0.0, immediate pass
 * </ul>
 *
 * <p>Thread safety: stateless after construction. Safe for concurrent use across virtual threads.
 */
public final class PromptInjectionLayer implements SentinelLayer {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionLayer.class);

    private final List<InjectionPattern> patterns;
    private final double blockThreshold;
    private final double flagThreshold;

    /**
     * @param patterns       detection patterns; must not be null or empty
     * @param blockThreshold score at or above which the request is blocked; must be in [0.0, 1.0]
     * @param flagThreshold  score at or above which the match is logged but not blocked;
     *                       must be in [0.0, 1.0] and {@code <= blockThreshold}
     * @throws IllegalArgumentException if any argument is out of range
     */
    public PromptInjectionLayer(List<InjectionPattern> patterns,
                                 double blockThreshold,
                                 double flagThreshold) {
        Objects.requireNonNull(patterns, "patterns must not be null");
        if (patterns.isEmpty())
            throw new IllegalArgumentException("patterns list must not be empty");
        if (blockThreshold < 0.0 || blockThreshold > 1.0)
            throw new IllegalArgumentException("blockThreshold must be in [0.0, 1.0], got " + blockThreshold);
        if (flagThreshold < 0.0 || flagThreshold > 1.0)
            throw new IllegalArgumentException("flagThreshold must be in [0.0, 1.0], got " + flagThreshold);
        if (blockThreshold < flagThreshold)
            throw new IllegalArgumentException(
                    "blockThreshold must be >= flagThreshold; got block=" + blockThreshold + " flag=" + flagThreshold);

        this.patterns       = List.copyOf(patterns);
        this.blockThreshold = blockThreshold;
        this.flagThreshold  = flagThreshold;
    }

    /** Constructs an instance using the default pattern library and standard thresholds. */
    public static PromptInjectionLayer withDefaults() {
        return new PromptInjectionLayer(InjectionPatternLibrary.defaults(), 0.75, 0.50);
    }

    @Override
    public String name() { return "injection_scanner"; }

    @Override
    public LayerDecision evaluate(SentinelRequest request, PipelineContext ctx) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(ctx,     "ctx must not be null");

        String body = request.body();
        if (body == null || body.isBlank()) {
            ctx.put(PipelineContext.KEY_INJECTION_SCORE, 0.0);
            return LayerDecision.pass();
        }

        List<String> matched  = new ArrayList<>();
        double maxSeverity    = 0.0;

        for (InjectionPattern pattern : patterns) {
            if (pattern.matches(body)) {
                matched.add(pattern.id());
                if (pattern.severity() > maxSeverity) {
                    maxSeverity = pattern.severity();
                }
            }
        }

        ctx.put(PipelineContext.KEY_INJECTION_SCORE, maxSeverity);

        if (maxSeverity >= blockThreshold) {
            log.warn("Prompt injection blocked: request={} patterns={} score={}",
                    request.requestId(), matched, maxSeverity);
            return LayerDecision.block(
                    "prompt-injection-detected",
                    "Potential prompt injection detected; matched patterns: " + matched,
                    "The request body contains patterns associated with prompt injection attacks. " +
                    "Sanitize the content and retry, or escalate to a human operator.",
                    List.of(),
                    maxSeverity
            );
        }

        if (maxSeverity >= flagThreshold) {
            log.info("Prompt injection flagged (non-blocking): request={} patterns={} score={}",
                    request.requestId(), matched, maxSeverity);
        }

        return LayerDecision.pass();
    }
}
