package io.sentinelgateway.core.financial;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-execution-thread budget tracker.
 *
 * <p>All counters use lock-free atomic primitives so concurrent virtual threads
 * representing the same logical agent invocation can safely share one instance.
 * {@link LongAdder} is used for the spend counter because it reduces contention
 * under high write frequency; read (via {@link LongAdder#sum()}) is eventually
 * consistent but sufficiently accurate for budget enforcement.
 *
 * <p>Thread safety: all public methods are safe for concurrent invocation.
 */
public final class ExecutionBudget {

    /** Scale factor: store USD as integer nano-cents to avoid floating-point accumulation error. */
    private static final long NANO_CENTS_PER_DOLLAR = 1_000_000_000L;

    private final String executionThreadId;
    private final BudgetConfig config;
    private final long windowStartNanos;

    private final LongAdder spendNanoCents = new LongAdder();
    private final AtomicLong tokensConsumed = new AtomicLong(0L);
    private final AtomicInteger mutationCount = new AtomicInteger(0);

    /**
     * @param executionThreadId identifies the logical agent invocation; must not be null or blank
     * @param config             budget limits to enforce; must not be null
     */
    public ExecutionBudget(String executionThreadId, BudgetConfig config) {
        if (executionThreadId == null || executionThreadId.isBlank())
            throw new IllegalArgumentException("executionThreadId must not be null or blank");
        this.executionThreadId = executionThreadId;
        this.config            = Objects.requireNonNull(config, "config must not be null");
        this.windowStartNanos  = System.nanoTime();
    }

    /**
     * Records an external API cost in USD.
     *
     * @param usd amount spent; must be non-negative
     * @throws IllegalArgumentException if {@code usd} is negative
     */
    public void recordSpend(double usd) {
        if (usd < 0.0)
            throw new IllegalArgumentException("spend amount must be non-negative, got " + usd);
        spendNanoCents.add((long) (usd * NANO_CENTS_PER_DOLLAR));
    }

    /**
     * Records LLM tokens consumed.
     *
     * @param tokens number of tokens; must be non-negative
     * @throws IllegalArgumentException if {@code tokens} is negative
     */
    public void recordTokens(long tokens) {
        if (tokens < 0L)
            throw new IllegalArgumentException("token count must be non-negative, got " + tokens);
        tokensConsumed.addAndGet(tokens);
    }

    /** Increments the mutation counter by one. */
    public void recordMutation() {
        mutationCount.incrementAndGet();
    }

    /** Returns accumulated spend in USD. */
    public double spendUsd() {
        return spendNanoCents.sum() / (double) NANO_CENTS_PER_DOLLAR;
    }

    /** Returns total tokens recorded. */
    public long tokensConsumed() { return tokensConsumed.get(); }

    /** Returns total mutations recorded. */
    public int mutationCount() { return mutationCount.get(); }

    /** Returns the execution thread identifier. */
    public String threadId() { return executionThreadId; }

    /** Returns {@code true} if the velocity window has elapsed since this budget was created. */
    public boolean isWindowExpired() {
        long elapsedMs = (System.nanoTime() - windowStartNanos) / 1_000_000L;
        return elapsedMs > config.velocityWindowMs();
    }

    /**
     * Checks all configured budget limits.
     *
     * @return a {@link BudgetViolation} describing the first exceeded limit,
     *         or {@code null} if all limits are within bounds
     */
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
                    String.format("Mutations %d reached limit %d within velocity window",
                            mutationCount(), config.maxStateMutations()));
        }
        return null;
    }

    /**
     * Describes a single budget limit violation.
     *
     * @param ruleId  machine-readable identifier for the violated rule
     * @param reason  human-readable explanation suitable for the agent guidance field
     */
    public record BudgetViolation(String ruleId, String reason) {
        public BudgetViolation {
            Objects.requireNonNull(ruleId, "ruleId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
