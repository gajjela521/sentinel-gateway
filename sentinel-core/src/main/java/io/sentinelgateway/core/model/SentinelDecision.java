package io.sentinelgateway.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The machine-readable rejection/approval envelope returned to the calling agent.
 * Structured so agents can reason about rejections and self-correct.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SentinelDecision(
        @JsonProperty("sentinel_decision")  Verdict sentinelDecision,
        @JsonProperty("layer")              String layer,
        @JsonProperty("rule_id")            String ruleId,
        @JsonProperty("reason")             String reason,
        @JsonProperty("safe_alternatives")  List<String> safeAlternatives,
        @JsonProperty("audit_trace_id")     String auditTraceId,
        @JsonProperty("agent_guidance")     String agentGuidance,
        @JsonProperty("scores")             ScoreDetail scores
) {
    public enum Verdict { ALLOW, BLOCK, QUARANTINE, REQUIRE_APPROVAL }

    public record ScoreDetail(
            double driftScore,
            double injectionScore,
            double schemaAnomalyScore,
            double mutationWeight
    ) {}

    public static SentinelDecision allow(String auditTraceId) {
        return new SentinelDecision(Verdict.ALLOW, null, null, null, null, auditTraceId, null, null);
    }

    public static Builder block() { return new Builder(Verdict.BLOCK); }
    public static Builder quarantine() { return new Builder(Verdict.QUARANTINE); }
    public static Builder requireApproval() { return new Builder(Verdict.REQUIRE_APPROVAL); }

    public boolean isAllowed() { return sentinelDecision == Verdict.ALLOW; }

    public static final class Builder {
        private final Verdict verdict;
        private String layer;
        private String ruleId;
        private String reason;
        private List<String> safeAlternatives;
        private String auditTraceId;
        private String agentGuidance;
        private ScoreDetail scores;

        private Builder(Verdict v) { this.verdict = v; }

        public Builder layer(String v)              { layer = v; return this; }
        public Builder ruleId(String v)             { ruleId = v; return this; }
        public Builder reason(String v)             { reason = v; return this; }
        public Builder safeAlternatives(List<String> v) { safeAlternatives = v; return this; }
        public Builder auditTraceId(String v)       { auditTraceId = v; return this; }
        public Builder agentGuidance(String v)      { agentGuidance = v; return this; }
        public Builder scores(ScoreDetail v)        { scores = v; return this; }

        public SentinelDecision build() {
            return new SentinelDecision(verdict, layer, ruleId, reason,
                    safeAlternatives, auditTraceId, agentGuidance, scores);
        }
    }
}
