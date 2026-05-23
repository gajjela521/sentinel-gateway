package io.sentinelgateway.core.policy;

import io.sentinelgateway.core.model.LayerDecision;
import io.sentinelgateway.core.model.SentinelRequest;
import io.sentinelgateway.core.pipeline.PipelineContext;
import io.sentinelgateway.core.pipeline.SentinelLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

/**
 * Layer 2: Evaluates ordered {@link PolicyRule} list against the request.
 * Rules are sorted by priority at construction time so evaluation is a simple
 * linear scan — fast enough for hundreds of rules in the hot path.
 */
public final class PolicyEngine implements SentinelLayer {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final List<PolicyRule> rules;

    public PolicyEngine(List<PolicyRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(PolicyRule::priority))
                .toList();
        log.info("PolicyEngine loaded {} rules", this.rules.size());
    }

    @Override
    public String name() { return "policy_engine"; }

    @Override
    public LayerDecision evaluate(SentinelRequest request, PipelineContext ctx) {
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
        // Method check
        if (!rule.methods().isEmpty() && !rule.methods().contains(req.method())) return false;

        // Endpoint pattern check
        if (rule.endpointPattern() != null
                && !rule.endpointPattern().matcher(req.endpoint()).matches()) return false;

        return true;
    }
}
