package io.sentinelgateway.core.pipeline;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable context bag shared across layers within one request's pipeline run.
 * Layers can store computed artefacts (embedding vector, schema diff, etc.) so
 * later layers can reuse them without recomputing.
 */
public final class PipelineContext {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) store.get(key);
    }

    public void put(String key, Object value) {
        store.put(key, value);
    }

    public boolean has(String key) {
        return store.containsKey(key);
    }

    // Typed convenience keys used across layers
    public static final String KEY_EMBEDDING       = "embedding.vector";
    public static final String KEY_DRIFT_SCORE     = "semantic.driftScore";
    public static final String KEY_INJECTION_SCORE = "injection.score";
    public static final String KEY_SCHEMA_ANOMALY  = "schema.anomalyScore";
    public static final String KEY_MUTATION_WEIGHT = "financial.mutationWeight";
    public static final String KEY_BUDGET_SNAPSHOT = "financial.budgetSnapshot";
}
