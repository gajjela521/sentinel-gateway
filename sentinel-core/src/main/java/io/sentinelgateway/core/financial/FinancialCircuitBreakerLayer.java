package io.sentinelgateway.core.financial;

import io.sentinelgateway.core.model.LayerDecision;
import io.sentinelgateway.core.model.SentinelRequest;
import io.sentinelgateway.core.pipeline.PipelineContext;
import io.sentinelgateway.core.pipeline.SentinelLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 5: Financial Circuit Breaker.
 *
 * Three-state circuit breaker per execution thread:
 *   CLOSED     → normal, budgets tracked
 *   OPEN       → all requests blocked until reset
 *   HALF_OPEN  → one probe allowed to test recovery
 *
 * Trips to OPEN when any budget (spend/tokens/mutations) is exceeded.
 * Mutation requests are pre-charged before execution to prevent over-run.
 */
public final class FinancialCircuitBreakerLayer implements SentinelLayer {

    private static final Logger log = LoggerFactory.getLogger(FinancialCircuitBreakerLayer.class);

    public enum BreakerState { CLOSED, OPEN, HALF_OPEN }

    private record BreakerEntry(ExecutionBudget budget, volatile BreakerState state) {}

    private final BudgetConfig defaultConfig;
    private final Map<String, BreakerEntry> breakers = new ConcurrentHashMap<>();

    public FinancialCircuitBreakerLayer(BudgetConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    @Override
    public String name() { return "financial_circuit_breaker"; }

    @Override
    public LayerDecision evaluate(SentinelRequest request, PipelineContext ctx) {
        String threadId = request.executionThreadId();
        if (threadId == null) return LayerDecision.pass(); // no tracking context

        BreakerEntry entry = breakers.computeIfAbsent(
                threadId, id -> new BreakerEntry(new ExecutionBudget(id, defaultConfig), BreakerState.CLOSED));

        if (entry.state() == BreakerState.OPEN) {
            // Check if window has expired — try half-open
            if (entry.budget().isWindowExpired()) {
                breakers.put(threadId, new BreakerEntry(new ExecutionBudget(threadId, defaultConfig), BreakerState.HALF_OPEN));
                log.info("Circuit breaker for thread {} transitioned to HALF_OPEN", threadId);
            } else {
                return LayerDecision.block(
                        "circuit-breaker-open",
                        "Financial circuit breaker is OPEN for execution thread " + threadId,
                        "Wait for the velocity window to expire or request human reset",
                        List.of(), 1.0
                );
            }
        }

        // Pre-charge mutations before they happen
        if (request.method().isMutation()) {
            entry.budget().recordMutation();
        }

        ExecutionBudget.BudgetViolation violation = entry.budget().checkViolations();
        if (violation != null) {
            log.warn("Budget violation for thread {}: {}", threadId, violation.reason());
            breakers.put(threadId, new BreakerEntry(entry.budget(), BreakerState.OPEN));
            return LayerDecision.block(
                    violation.ruleId(),
                    violation.reason(),
                    "The financial circuit breaker has tripped. Request human approval to increase budgets or reset.",
                    List.of(), 1.0
            );
        }

        ctx.put(PipelineContext.KEY_BUDGET_SNAPSHOT, new BudgetSnapshot(
                entry.budget().spendUsd(),
                entry.budget().tokensConsumed(),
                entry.budget().mutationCount(),
                entry.state()
        ));

        return LayerDecision.pass();
    }

    /** Allow external cost reporters to update spend after a downstream call completes. */
    public void recordSpend(String executionThreadId, double usd, long tokens) {
        BreakerEntry entry = breakers.get(executionThreadId);
        if (entry != null) {
            entry.budget().recordSpend(usd);
            entry.budget().recordTokens(tokens);
        }
    }

    public BreakerState getState(String executionThreadId) {
        BreakerEntry entry = breakers.get(executionThreadId);
        return entry != null ? entry.state() : BreakerState.CLOSED;
    }

    public record BudgetSnapshot(
            double spendUsd,
            long tokensConsumed,
            int mutationCount,
            BreakerState state
    ) {}
}
