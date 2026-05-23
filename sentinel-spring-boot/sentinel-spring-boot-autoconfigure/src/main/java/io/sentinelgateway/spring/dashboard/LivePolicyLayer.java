package io.sentinelgateway.spring.dashboard;

import io.sentinelgateway.core.model.LayerDecision;
import io.sentinelgateway.core.model.SentinelRequest;
import io.sentinelgateway.core.pipeline.PipelineContext;
import io.sentinelgateway.core.pipeline.SentinelLayer;
import io.sentinelgateway.core.policy.PolicyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * A policy layer that reads rules from {@link PolicyStore} on every evaluation,
 * enabling hot updates via the dashboard API without restarting the pipeline.
 *
 * <p>Rule evaluation follows the same priority-ordered linear scan as
 * {@code PolicyEngine} — the lowest priority number wins first match.
 */
public final class LivePolicyLayer implements SentinelLayer {

    private static final Logger log = LoggerFactory.getLogger(LivePolicyLayer.class);

    private final PolicyStore store;

    public LivePolicyLayer(PolicyStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public String name() { return "policy_engine"; }

    @Override
    public LayerDecision evaluate(SentinelRequest request, PipelineContext ctx) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(ctx,     "ctx must not be null");

        List<PolicyRule> rules = store.list(); // sorted by priority, snapshot of current state

        for (PolicyRule rule : rules) {
            if (!matches(rule, request)) continue;

            log.debug("Rule {} matched request {}", rule.id(), request.requestId());

            return switch (rule.action()) {
                case ALLOW -> LayerDecision.pass();
                case BLOCK -> LayerDecision.block(
                        rule.id(),
                        rule.description(),
                        rule.agentGuidance(),
                        rule.safeAlternatives(),
                        1.0
                );
                case QUARANTINE -> LayerDecision.quarantine(rule.id(), rule.description(), 1.0);
                case REQUIRE_APPROVAL -> LayerDecision.requireApproval(
                        rule.id(), rule.description(), rule.agentGuidance(),
                        rule.safeAlternatives());
            };
        }
        return LayerDecision.pass();
    }

    private boolean matches(PolicyRule rule, SentinelRequest req) {
        if (!rule.methods().isEmpty() && !rule.methods().contains(req.method())) return false;
        if (rule.endpointPattern() != null
                && !rule.endpointPattern().matcher(req.endpoint()).matches()) return false;
        return true;
    }
}
