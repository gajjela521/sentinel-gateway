package io.sentinelgateway.core.financial;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per execution-thread budget tracker.
 * All fields are atomically updated — safe to share across virtual threads
 * that represent the same logical agent invocation.
 */
public final class ExecutionBudget {

    private final String executionThreadId;
    private final BudgetConfig config;
    private final long windowStartNanos;

    private final LongAdder spendNanoCents = new LongAdder();  // USD * 1e9 to avoid float
    private final AtomicLong tokensConsumed = new AtomicLong(0);
    private final AtomicInteger mutationCount = new AtomicInteger(0);

    public ExecutionBudget(String executionThreadId, BudgetConfig config) {
        this.executionThreadId = executionThreadId;
        this.config = config;
        this.windowStartNanos = System.nanoTime();
    }

    public void recordSpend(double usd) {
        spendNanoCents.add((long) (usd * 1_000_000_000L));
    }

    public void recordTokens(long tokens) {
        tokensConsumed.addAndGet(tokens);
    }

    public void recordMutation() {
        mutationCount.incrementAndGet();
    }

    public double spendUsd() {
        return spendNanoCents.sum() / 1_000_000_000.0;
    }

    public long tokensConsumed() { return tokensConsumed.get(); }
    public int mutationCount()   { return mutationCount.get(); }
    public String threadId()     { return executionThreadId; }

    public boolean isWindowExpired() {
        long elapsedMs = (System.nanoTime() - windowStartNanos) / 1_000_000L;
        return elapsedMs > config.velocityWindowMs();
    }

    public BudgetViolation checkViolations() {
        if (spendUsd() > config.maxSpendUsd()) {
            return new BudgetViolation("spend-cap-exceeded",
                    String.format("Spend $%.4f exceeds limit $%.4f", spendUsd(), config.maxSpendUsd()));
        }
        if (tokensConsumed() > config.maxTokensBurned()) {
            return new BudgetViolation("token-cap-exceeded",
                    String.format("Tokens %d exceeds limit %d", tokensConsumed(), config.maxTokensBurned()));
        }
        if (!isWindowExpired() && mutationCount() >= config.maxStateMutations()) {
            return new BudgetViolation("mutation-velocity-exceeded",
                    String.format("Mutations %d exceeds limit %d in velocity window",
                            mutationCount(), config.maxStateMutations()));
        }
        return null;
    }

    public record BudgetViolation(String ruleId, String reason) {}
}
