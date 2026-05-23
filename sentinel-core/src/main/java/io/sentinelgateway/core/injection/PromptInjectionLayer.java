package io.sentinelgateway.core.injection;

import io.sentinelgateway.core.model.LayerDecision;
import io.sentinelgateway.core.model.SentinelRequest;
import io.sentinelgateway.core.pipeline.PipelineContext;
import io.sentinelgateway.core.pipeline.SentinelLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer 3: Prompt Injection Scanner.
 *
 * Scans the request body for known injection patterns from
 * {@link InjectionPatternLibrary}. The aggregate score is the max severity
 * of all matching patterns (not sum, to avoid penalizing benign text that
 * coincidentally matches multiple weak signals).
 *
 * Score > blockThreshold → BLOCK
 * Score > flagThreshold  → adds warning metadata but continues
 */
public final class PromptInjectionLayer implements SentinelLayer {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionLayer.class);

    private final List<InjectionPattern> patterns;
    private final double blockThreshold;
    private final double flagThreshold;

    public PromptInjectionLayer(List<InjectionPattern> patterns,
                                 double blockThreshold,
                                 double flagThreshold) {
        this.patterns = List.copyOf(patterns);
        this.blockThreshold = blockThreshold;
        this.flagThreshold = flagThreshold;
    }

    public static PromptInjectionLayer withDefaults() {
        return new PromptInjectionLayer(InjectionPatternLibrary.defaults(), 0.75, 0.50);
    }

    @Override
    public String name() { return "injection_scanner"; }

    @Override
    public LayerDecision evaluate(SentinelRequest request, PipelineContext ctx) {
        String body = request.body();
        if (body == null || body.isBlank()) {
            ctx.put(PipelineContext.KEY_INJECTION_SCORE, 0.0);
            return LayerDecision.pass();
        }

        List<String> matched = new ArrayList<>();
        double maxSeverity = 0.0;

        for (InjectionPattern pattern : patterns) {
            if (pattern.matches(body)) {
                matched.add(pattern.id());
                maxSeverity = Math.max(maxSeverity, pattern.severity());
            }
        }

        ctx.put(PipelineContext.KEY_INJECTION_SCORE, maxSeverity);

        if (maxSeverity >= blockThreshold) {
            log.warn("Injection detected in request {} — patterns: {}, score: {}",
                    request.requestId(), matched, maxSeverity);
            return LayerDecision.block(
                    "prompt-injection-detected",
                    "Potential prompt injection detected: " + matched,
                    "The request body contains patterns associated with prompt injection. " +
                    "Review and sanitize the content before retrying.",
                    List.of(),
                    maxSeverity
            );
        }

        if (maxSeverity >= flagThreshold) {
            log.debug("Injection flag (non-blocking) in request {} — score: {}", request.requestId(), maxSeverity);
        }

        return LayerDecision.pass();
    }
}
