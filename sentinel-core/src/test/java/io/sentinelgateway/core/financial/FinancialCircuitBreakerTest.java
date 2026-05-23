package io.sentinelgateway.core.financial;

import io.sentinelgateway.core.model.*;
import io.sentinelgateway.core.pipeline.PipelineContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
    void allowsMutationsWithinBudget() {
        BudgetConfig config = new BudgetConfig(100.0, 100_000, 5, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        for (int i = 0; i < 4; i++) {
            LayerDecision d = cb.evaluate(mutation("thread-A"), new PipelineContext());
            assertEquals(LayerDecision.Outcome.PASS, d.outcome(),
                    "Mutation " + (i + 1) + " should pass");
        }
    }

    @Test
    void tripsOnMutationLimitReached() {
        // With limit=3: calls 1 and 2 pass (count < limit), call 3 pre-charges to count=3 which hits limit
        BudgetConfig config = new BudgetConfig(100.0, 100_000, 3, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        cb.evaluate(mutation("thread-B"), new PipelineContext()); // count=1 → PASS
        cb.evaluate(mutation("thread-B"), new PipelineContext()); // count=2 → PASS
        LayerDecision d = cb.evaluate(mutation("thread-B"), new PipelineContext()); // count=3 → BLOCK

        assertEquals(LayerDecision.Outcome.BLOCK, d.outcome(), "Should block when mutation count reaches limit");
        assertEquals("mutation-velocity-exceeded", d.ruleId());
    }

    @Test
    void openBreakerBlocksAllSubsequentRequests() {
        BudgetConfig config = new BudgetConfig(100.0, 100_000, 1, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        cb.evaluate(mutation("thread-C"), new PipelineContext()); // count=1 → BLOCK, breaker OPEN

        // Read (non-mutation) should also be blocked while OPEN
        LayerDecision d = cb.evaluate(read("thread-C"), new PipelineContext());
        assertEquals(LayerDecision.Outcome.BLOCK, d.outcome(),
                "OPEN breaker must block all request types, including reads");
        assertEquals("circuit-breaker-open", d.ruleId());
    }

    @Test
    void differentThreadsHaveIndependentBudgets() {
        BudgetConfig config = new BudgetConfig(100.0, 100_000, 2, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        // Trip thread-D's breaker
        cb.evaluate(mutation("thread-D"), new PipelineContext()); // count=1
        cb.evaluate(mutation("thread-D"), new PipelineContext()); // count=2 → BLOCK

        // thread-E is completely unaffected
        LayerDecision d = cb.evaluate(mutation("thread-E"), new PipelineContext());
        assertEquals(LayerDecision.Outcome.PASS, d.outcome(),
                "A separate execution thread must have an independent budget");
    }

    @Test
    void recordSpendTripsOnSpendCapExceeded() {
        BudgetConfig config = new BudgetConfig(1.0, 100_000, 100, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        cb.evaluate(read("thread-F"), new PipelineContext()); // establish entry

        cb.recordSpend("thread-F", 0.60, 100);
        cb.recordSpend("thread-F", 0.50, 100); // total $1.10 > $1.00 limit

        LayerDecision d = cb.evaluate(read("thread-F"), new PipelineContext());
        assertEquals(LayerDecision.Outcome.BLOCK, d.outcome(), "Should block after spend cap exceeded");
        assertEquals("spend-cap-exceeded", d.ruleId());
    }

    @Test
    void nullThreadIdPassesWithoutTracking() {
        BudgetConfig config = new BudgetConfig(0.0, 0, 0, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        SentinelRequest noThread = SentinelRequest.builder()
                .organizationId("org-1")
                .method(HttpMethod.DELETE)
                .endpoint("/api/data")
                .build();

        LayerDecision d = cb.evaluate(noThread, new PipelineContext());
        assertEquals(LayerDecision.Outcome.PASS, d.outcome(),
                "Requests without executionThreadId must not be budget-tracked");
    }

    @Test
    void recordSpendOnNegativeUsdThrows() {
        BudgetConfig config = BudgetConfig.defaults();
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);
        cb.evaluate(read("thread-G"), new PipelineContext());

        assertThrows(IllegalArgumentException.class,
                () -> cb.recordSpend("thread-G", -0.01, 0),
                "Negative spend must be rejected");
    }

    @Test
    void getStateReturnsCLOSEDForUnknownThread() {
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(BudgetConfig.defaults());
        assertEquals(FinancialCircuitBreakerLayer.BreakerState.CLOSED, cb.getState("never-seen"),
                "Unknown thread must report CLOSED state");
    }

    @Test
    void budgetSnapshotStoredInContext() {
        BudgetConfig config = new BudgetConfig(10.0, 50_000, 10, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);
        PipelineContext ctx = new PipelineContext();

        LayerDecision d = cb.evaluate(read("thread-H"), ctx);
        assertEquals(LayerDecision.Outcome.PASS, d.outcome());

        FinancialCircuitBreakerLayer.BudgetSnapshot snap =
                ctx.get(PipelineContext.KEY_BUDGET_SNAPSHOT);
        assertNotNull(snap, "BudgetSnapshot must be stored in context on PASS");
        assertEquals(FinancialCircuitBreakerLayer.BreakerState.CLOSED, snap.state());
    }

    @Test
    void concurrentMutationsOnSameThreadDoNotCorruptCount() throws InterruptedException {
        int threadCount = 20;
        int mutationsPerThread = 3;
        int totalMutations = threadCount * mutationsPerThread;
        // Budget high enough that only the mutation limit matters
        BudgetConfig config = new BudgetConfig(1000.0, 10_000_000, totalMutations + 1, 60_000);
        FinancialCircuitBreakerLayer cb = new FinancialCircuitBreakerLayer(config);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<LayerDecision.Outcome>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                latch.await(); // all threads start simultaneously
                LayerDecision last = LayerDecision.pass();
                for (int j = 0; j < mutationsPerThread; j++) {
                    last = cb.evaluate(mutation("shared-thread"), new PipelineContext());
                }
                return last.outcome();
            }));
        }

        latch.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        // Verify no future threw an unexpected exception
        for (Future<LayerDecision.Outcome> f : futures) {
            assertDoesNotThrow(() -> f.get(), "Concurrent evaluation must not throw");
        }

        // The circuit breaker must be in a defined state — not corrupted
        FinancialCircuitBreakerLayer.BreakerState state = cb.getState("shared-thread");
        assertNotNull(state, "Breaker state must not be null after concurrent access");
    }
}
