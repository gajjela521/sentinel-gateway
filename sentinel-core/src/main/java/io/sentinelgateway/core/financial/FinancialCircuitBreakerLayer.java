package io.sentinelgateway.core.financial;

import io.sentinelgateway.core.model.LayerDecision;
import io.sentinelgateway.core.model.SentinelRequest;
import io.sentinelgateway.core.pipeline.PipelineContext;
import io.sentinelgateway.core.pipeline.SentinelLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 4: Financial Circuit Breaker.
 *
 * <p>Enforces per-execution-thread budgets for spend, token consumption, and
 * mutation velocity. When any limit is exceeded the breaker transitions to
 * {@link BreakerState#OPEN} and blocks all subsequent requests for the same
 * thread until the velocity window expires, at which point a single probe is
 * allowed ({@link BreakerState#HALF_OPEN}).
 *
 * <h3>Thread safety</h3>
 * <p>All state transitions use {@link ConcurrentHashMap#compute} which is atomic
 * per-key. No external synchronization is required. {@link ExecutionBudget}
 * counters are themselves lock-free.
 *
 * <h3>Budget independence</h3>
 * <p>Each execution thread ID has a fully isolated budget. A tripped breaker on
 * one thread has zero effect on any other thread.
 */
public final class FinancialCircuitBreakerLayer implements SentinelLayer {

    private static final Logger log = LoggerFactory.getLogger(FinancialCircuitBreakerLayer.class);

    /** Observable state of one execution thread's circuit breaker. */
    public enum BreakerState { CLOSED, OPEN, HALF_OPEN }

    /**
     * Mutable per-thread breaker state. Kept as a plain class (not record) because
     * {@code state} must be {@code volatile} for cross-thread visibility without
     * a lock — record components cannot carry the {@code volatile} modifier.
     */
    private static final class BreakerEntry {
        final ExecutionBudget budget;
        volatile BreakerState state;

        BreakerEntry(ExecutionBudget budget, BreakerState initialState) {
            this.budget = Objects.requireNonNull(budget, "budget must not be null");
            this.state  = Objects.requireNonNull(initialState, "initialState must not be null");
        }
    }

    private final BudgetConfig defaultConfig;
    private final Map<String, BreakerEntry> breakers = new ConcurrentHashMap<>();

    /**
     * @param defaultConfig budget limits applied to every new execution thread; must not be null
     */
    public FinancialCircuitBreakerLayer(BudgetConfig defaultConfig) {
        this.defaultConfig = Objects.requireNonNull(defaultConfig, "defaultConfig must not be null");
    }

    @Override
    public String name() { return "financial_circuit_breaker"; }

    @Override
    public LayerDecision evaluate(SentinelRequest request, PipelineContext ctx) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        String threadId = request.executionThreadId();
        if (threadId == null || threadId.isBlank()) {
            // No tracking context — pass through without budget enforcement
            return LayerDecision.pass();
        }

        /*
         * Atomically ensure the entry exists, then check its current state.
         * The compute() call below handles the OPEN → HALF_OPEN transition atomically,
         * eliminating the TOCTOU race that would exist with a separate get() + put().
         */
        breakers.computeIfAbsent(
                threadId,
                id -> new BreakerEntry(new ExecutionBudget(id, defaultConfig), BreakerState.CLOSED));

        // Atomic OPEN → HALF_OPEN transition when the velocity window has expired
        breakers.compute(threadId, (id, entry) -> {
            if (entry != null
                    && entry.state == BreakerState.OPEN
                    && entry.budget.isWindowExpired()) {
                log.info("Circuit breaker transitioning OPEN -> HALF_OPEN for thread={}", id);
                entry.state = BreakerState.HALF_OPEN;
            }
            return entry;
        });

        BreakerEntry entry = breakers.get(threadId);

        if (entry.state == BreakerState.OPEN) {
            return LayerDecision.block(
                    "circuit-breaker-open",
                    "Financial circuit breaker is OPEN for execution thread: " + threadId,
                    "Wait for the velocity window to expire or request human approval to reset.",
                    List.of(),
                    1.0
            );
        }

        // Pre-charge mutation cost before execution — prevents budget over-run
        if (request.method().isMutation()) {
            entry.budget.recordMutation();
        }

        ExecutionBudget.BudgetViolation violation = entry.budget.checkViolations();
        if (violation != null) {
            log.warn("Budget violation thread={} rule={} reason={}",
                    threadId, violation.ruleId(), violation.reason());

            // Atomic trip to OPEN
            breakers.compute(threadId, (id, e) -> {
                if (e != null) e.state = BreakerState.OPEN;
                return e;
            });

            return LayerDecision.block(
                    violation.ruleId(),
                    violation.reason(),
                    "The financial circuit breaker has tripped. Request human approval to increase " +
                    "budgets or reset the execution thread.",
                    List.of(),
                    1.0
            );
        }

        ctx.put(PipelineContext.KEY_BUDGET_SNAPSHOT, new BudgetSnapshot(
                entry.budget.spendUsd(),
                entry.budget.tokensConsumed(),
                entry.budget.mutationCount(),
                entry.state
        ));

        return LayerDecision.pass();
    }

    /**
     * Reports actual external API cost after a downstream call completes.
     * Callers should invoke this after every successful downstream response.
     *
     * @param executionThreadId thread to charge; silently ignored if untracked
     * @param usd   cost in USD; must be non-negative
     * @param tokens LLM tokens consumed; must be non-negative
     */
    public void recordSpend(String executionThreadId, double usd, long tokens) {
        if (executionThreadId == null || executionThreadId.isBlank()) return;
        BreakerEntry entry = breakers.get(executionThreadId);
        if (entry != null) {
            entry.budget.recordSpend(usd);
            entry.budget.recordTokens(tokens);
        }
    }

    /**
     * Returns the current circuit breaker state for the given thread.
     * Returns {@link BreakerState#CLOSED} if the thread has no tracked history.
     */
    public BreakerState getState(String executionThreadId) {
        if (executionThreadId == null || executionThreadId.isBlank())
            return BreakerState.CLOSED;
        BreakerEntry entry = breakers.get(executionThreadId);
        return entry != null ? entry.state : BreakerState.CLOSED;
    }

    /**
     * Point-in-time snapshot of budget consumption for a single execution thread.
     * Attached to {@link PipelineContext} so downstream layers and audit sinks can read it.
     */
    public record BudgetSnapshot(
            double spendUsd,
            long tokensConsumed,
            int mutationCount,
            BreakerState state
    ) {}
}
