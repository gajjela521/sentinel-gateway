package io.sentinelgateway.core.pipeline;

import io.sentinelgateway.core.model.LayerDecision;
import io.sentinelgateway.core.model.SentinelRequest;

/**
 * Single stage in the 6-layer processing pipeline.
 * Implementations must be thread-safe; they are shared across virtual threads.
 */
public interface SentinelLayer {

    /** Short name used in audit logs and metrics labels (e.g. "policy_engine"). */
    String name();

    /**
     * Evaluate the request and return a pass-or-terminal decision.
     * Never throw checked exceptions — wrap in {@link LayerDecision#block} with a safe message.
     */
    LayerDecision evaluate(SentinelRequest request, PipelineContext ctx);
}
