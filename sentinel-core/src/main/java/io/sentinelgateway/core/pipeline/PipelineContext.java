package io.sentinelgateway.core.pipeline;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable context bag shared across all layers within a single request's pipeline run.
 *
 * <p>Layers store computed artefacts here (embedding vectors, anomaly scores, etc.) so that
 * later layers can reuse them without redundant computation. All access is through the
 * typed key constants below to prevent key collisions between layers.
 *
 * <p><strong>Usage contract:</strong>
 * <ul>
 *   <li>Each context instance is created per-request and is never shared across requests.
 *   <li>Layers may read keys written by earlier layers; they must not modify keys they do not own.
 *   <li>The unchecked cast in {@link #get} is safe as long as callers use the typed constants below.
 * </ul>
 *
 * <p>Thread safety: backed by {@link ConcurrentHashMap} to allow layers that fork virtual
 * threads internally. Single-threaded pipeline execution is the common case.
 */
public final class PipelineContext {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    /**
     * Returns the value associated with {@code key}, or {@code null} if absent.
     * The unchecked cast is intentional: key constants guarantee type correctness at the call site.
     *
     * @param key must not be null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return (T) store.get(key);
    }

    /**
     * Stores a value under {@code key}.
     *
     * @param key   must not be null
     * @param value must not be null
     */
    public void put(String key, Object value) {
        Objects.requireNonNull(key,   "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        store.put(key, value);
    }

    /** Returns {@code true} if a value has been stored under {@code key}. */
    public boolean has(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return store.containsKey(key);
    }

    // ── Typed key constants (owners listed for contract clarity) ──────────────

    /** float[] — written by SemanticDriftLayer; the raw embedding vector for this request. */
    public static final String KEY_EMBEDDING       = "embedding.vector";

    /** Double — written by SemanticDriftLayer; the composite drift score in [0.0, 1.0+]. */
    public static final String KEY_DRIFT_SCORE     = "semantic.driftScore";

    /** Double — written by PromptInjectionLayer; max severity of any matched injection pattern. */
    public static final String KEY_INJECTION_SCORE = "injection.score";

    /** Double — written by callers that compute schema diffs; structural anomaly score in [0.0, 1.0]. */
    public static final String KEY_SCHEMA_ANOMALY  = "schema.anomalyScore";

    /** Double — written by SemanticDriftLayer; 1.0 if the request is a mutation, 0.0 otherwise. */
    public static final String KEY_MUTATION_WEIGHT = "financial.mutationWeight";

    /** FinancialCircuitBreakerLayer.BudgetSnapshot — written by FinancialCircuitBreakerLayer. */
    public static final String KEY_BUDGET_SNAPSHOT = "financial.budgetSnapshot";
}
