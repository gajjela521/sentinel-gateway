package io.sentinelgateway.core.financial;

import io.sentinelgateway.core.model.*;
import io.sentinelgateway.core.pipeline.PipelineContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FinancialCircuitBreakerTest {

    private SentinelRequest mutation(String threadId) {
        return SentinelRequest.builder()
                .organizationId("org-1")
                .agentId("agent-1")
                .executionThreadId(threadId)
                .method(HttpMethod.DELETE)
                .endpoint("/api/records/1")
                .build();
    }

    private SentinelRequest read(String threadId) {
        return SentinelRequest.builder()
                .organizationId("org-1")
                .agentId("agent-1")
                .executionThreadId(threadId)
                .method(HttpMethod.GET)
                .endpoint("/api/records")
                .build();
    }

    @Test
    void allowsRequestsWithinMutationBudget() {
        BudgetConfig config = new BudgetConfig(100.0, 100_000, 5, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        for (int i = 0; i < 4; i++) {
            LayerDecision d = cb.evaluate(mutation("thread-A"), new PipelineContext());
            assertEquals(LayerDecision.Outcome.PASS, d.outcome(),
                    "Mutation " + (i + 1) + " should pass");
        }
    }

    @Test
    void tripsCircuitBreakerOnMutationVelocityExceeded() {
        BudgetConfig config = new BudgetConfig(100.0, 100_000, 3, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        cb.evaluate(mutation("thread-B"), new PipelineContext());
        cb.evaluate(mutation("thread-B"), new PipelineContext());
        cb.evaluate(mutation("thread-B"), new PipelineContext());

        LayerDecision d = cb.evaluate(mutation("thread-B"), new PipelineContext());
        assertEquals(LayerDecision.Outcome.BLOCK, d.outcome(), "Should block after velocity exceeded");
        assertEquals("mutation-velocity-exceeded", d.ruleId());
    }

    @Test
    void openBreakerBlocksSubsequentRequests() {
        BudgetConfig config = new BudgetConfig(100.0, 100_000, 1, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        // trip the breaker
        cb.evaluate(mutation("thread-C"), new PipelineContext());
        cb.evaluate(mutation("thread-C"), new PipelineContext()); // trips

        // subsequent read (non-mutation) should also be blocked while OPEN
        LayerDecision d = cb.evaluate(read("thread-C"), new PipelineContext());
        assertEquals(LayerDecision.Outcome.BLOCK, d.outcome(),
                "OPEN breaker should block all requests including reads");
    }

    @Test
    void differentThreadsHaveIndependentBudgets() {
        BudgetConfig config = new BudgetConfig(100.0, 100_000, 2, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        // trip thread-D
        cb.evaluate(mutation("thread-D"), new PipelineContext());
        cb.evaluate(mutation("thread-D"), new PipelineContext());
        cb.evaluate(mutation("thread-D"), new PipelineContext());

        // thread-E should be unaffected
        LayerDecision d = cb.evaluate(mutation("thread-E"), new PipelineContext());
        assertEquals(LayerDecision.Outcome.PASS, d.outcome(),
                "Separate thread should have independent budget");
    }

    @Test
    void recordSpendTripsOnSpendCapExceeded() {
        BudgetConfig config = new BudgetConfig(1.0, 100_000, 100, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        // first call establishes the budget entry
        cb.evaluate(read("thread-F"), new PipelineContext());

        // simulate expensive downstream calls
        cb.recordSpend("thread-F", 0.60, 1000);
        cb.recordSpend("thread-F", 0.50, 1000); // total $1.10 > $1.00 limit

        LayerDecision d = cb.evaluate(read("thread-F"), new PipelineContext());
        assertEquals(LayerDecision.Outcome.BLOCK, d.outcome(), "Should block after spend cap exceeded");
    }
}
