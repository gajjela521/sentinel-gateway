package io.sentinelgateway.core.financial;

/**
 * Financial guardrails for a single agent execution thread.
 * All fields are per-thread, not global.
 */
public record BudgetConfig(
        double maxSpendUsd,        // hard cap on external API costs for this invocation
        long   maxTokensBurned,    // LLM token budget
        int    maxStateMutations,  // max destructive operations allowed in velocity window
        long   velocityWindowMs    // time window for mutation rate check
) {
    public static BudgetConfig defaults() {
        return new BudgetConfig(10.0, 50_000, 5, 60_000);
    }

    public static BudgetConfig strict() {
        return new BudgetConfig(1.0, 10_000, 2, 30_000);
    }

    public static BudgetConfig permissive() {
        return new BudgetConfig(100.0, 500_000, 50, 300_000);
    }
}
