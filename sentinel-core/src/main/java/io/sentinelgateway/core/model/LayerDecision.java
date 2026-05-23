package io.sentinelgateway.core.model;

/**
 * Decision returned by a single pipeline layer.
 * PASS → continue to next layer.
 * BLOCK/QUARANTINE/REQUIRE_APPROVAL → pipeline short-circuits and returns immediately.
 */
public record LayerDecision(
        Outcome outcome,
        String ruleId,
        String reason,
        String agentGuidance,
        java.util.List<String> safeAlternatives,
        double score
) {
    public enum Outcome { PASS, BLOCK, QUARANTINE, REQUIRE_APPROVAL }

    public boolean isTerminal() { return outcome != Outcome.PASS; }

    public static LayerDecision pass() {
        return new LayerDecision(Outcome.PASS, null, null, null, null, 0.0);
    }

    public static LayerDecision block(String ruleId, String reason, String agentGuidance,
                                       java.util.List<String> alts, double score) {
        return new LayerDecision(Outcome.BLOCK, ruleId, reason, agentGuidance, alts, score);
    }

    public static LayerDecision quarantine(String ruleId, String reason, double score) {
        return new LayerDecision(Outcome.QUARANTINE, ruleId, reason, null, null, score);
    }

    public static LayerDecision requireApproval(String ruleId, String reason, String guidance) {
        return new LayerDecision(Outcome.REQUIRE_APPROVAL, ruleId, reason, guidance, null, 0.0);
    }

    public SentinelDecision.Verdict toVerdict() {
        return switch (outcome) {
            case PASS             -> SentinelDecision.Verdict.ALLOW;
            case BLOCK            -> SentinelDecision.Verdict.BLOCK;
            case QUARANTINE       -> SentinelDecision.Verdict.QUARANTINE;
            case REQUIRE_APPROVAL -> SentinelDecision.Verdict.REQUIRE_APPROVAL;
        };
    }
}
