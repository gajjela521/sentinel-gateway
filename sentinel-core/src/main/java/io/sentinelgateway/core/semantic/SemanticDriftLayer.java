package io.sentinelgateway.core.semantic;

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
 * Layer 3: Semantic Drift Detection.
 *
 * Detects behavioral drift by comparing incoming requests against an
 * organization-specific baseline built from historic legitimate traffic.
 *
 * <p>Score formula: {@code driftScore = α*(1−cosineSim) + β*mutationWeight + γ*schemaAnomalyScore}
 *
 * <p>Thread safety: all per-org state lives in {@link OrgBaseline} instances which are
 * individually thread-safe. The {@code baselines} map uses {@link ConcurrentHashMap}.
 */
public final class SemanticDriftLayer implements SentinelLayer {

    private static final Logger log = LoggerFactory.getLogger(SemanticDriftLayer.class);

    /**
     * Immutable configuration for drift scoring weights and thresholds.
     *
     * @param alpha   weight applied to (1 - cosine similarity); range [0.0, 1.0]
     * @param beta    weight applied to mutation penalty; range [0.0, 1.0]
     * @param gamma   weight applied to schema anomaly score; range [0.0, 1.0]
     * @param threshold drift score above this value triggers QUARANTINE; range (0.0, 1.0]
     * @param minBaselineSamples minimum observations before drift scoring is active
     * @param updateBaselineOnAllow whether allowed requests contribute to baseline learning
     */
    public record DriftConfig(
            double alpha,
            double beta,
            double gamma,
            double threshold,
            int minBaselineSamples,
            boolean updateBaselineOnAllow
    ) {
        public DriftConfig {
            if (alpha < 0.0 || alpha > 1.0)
                throw new IllegalArgumentException("alpha must be in [0.0, 1.0], got " + alpha);
            if (beta < 0.0 || beta > 1.0)
                throw new IllegalArgumentException("beta must be in [0.0, 1.0], got " + beta);
            if (gamma < 0.0 || gamma > 1.0)
                throw new IllegalArgumentException("gamma must be in [0.0, 1.0], got " + gamma);
            if (threshold <= 0.0 || threshold > 1.0)
                throw new IllegalArgumentException("threshold must be in (0.0, 1.0], got " + threshold);
            if (minBaselineSamples < 0)
                throw new IllegalArgumentException("minBaselineSamples must be non-negative, got " + minBaselineSamples);
        }

        public static DriftConfig defaults() {
            return new DriftConfig(0.5, 0.3, 0.2, 0.65, 50, true);
        }
    }

    private final EmbeddingModel embeddingModel;
    private final DriftConfig config;
    private final Map<String, OrgBaseline> baselines = new ConcurrentHashMap<>();

    public SemanticDriftLayer(EmbeddingModel embeddingModel, DriftConfig config) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public String name() { return "semantic_drift"; }

    @Override
    public LayerDecision evaluate(SentinelRequest request, PipelineContext ctx) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(ctx, "ctx must not be null");

        if (!embeddingModel.isAvailable()) {
            log.debug("Embedding model unavailable; skipping drift check for request {}", request.requestId());
            return LayerDecision.pass();
        }

        float[] embedding = embeddingModel.embed(request.toEmbeddingText());
        ctx.put(PipelineContext.KEY_EMBEDDING, embedding);

        OrgBaseline baseline = baselines.computeIfAbsent(
                request.organizationId(),
                id -> new OrgBaseline(id, embeddingModel.dimension())
        );

        double cosineSim    = baseline.cosineSimilarity(embedding, config.minBaselineSamples());
        double mutationWeight = request.method().isMutation() ? 1.0 : 0.0;

        Double schemaAnomaly = ctx.get(PipelineContext.KEY_SCHEMA_ANOMALY);
        double schemaScore   = schemaAnomaly != null ? schemaAnomaly : 0.0;

        double driftScore = config.alpha() * (1.0 - cosineSim)
                + config.beta()  * mutationWeight
                + config.gamma() * schemaScore;

        ctx.put(PipelineContext.KEY_DRIFT_SCORE,    driftScore);
        ctx.put(PipelineContext.KEY_MUTATION_WEIGHT, mutationWeight);

        log.debug("Drift score={} cosine={} request={}", driftScore, cosineSim, request.requestId());

        if (driftScore > config.threshold()) {
            log.warn("Semantic drift detected: score={} threshold={} org={} request={}",
                    driftScore, config.threshold(), request.organizationId(), request.requestId());
            return LayerDecision.quarantine(
                    "semantic-drift-exceeded",
                    String.format("Drift score %.3f exceeds threshold %.3f", driftScore, config.threshold()),
                    driftScore
            );
        }

        if (config.updateBaselineOnAllow()) {
            baseline.update(embedding);
        }

        return LayerDecision.pass();
    }

    /**
     * Pre-warm an org baseline from a corpus of known-good request texts.
     * Call this at startup before live traffic is processed.
     *
     * @param orgId  organization identifier; must not be null or blank
     * @param corpus list of serialized request texts; must not be null or empty
     */
    public void bootstrapBaseline(String orgId, List<String> corpus) {
        if (orgId == null || orgId.isBlank())
            throw new IllegalArgumentException("orgId must not be null or blank");
        Objects.requireNonNull(corpus, "corpus must not be null");
        if (corpus.isEmpty())
            throw new IllegalArgumentException("corpus must not be empty");

        OrgBaseline baseline = baselines.computeIfAbsent(
                orgId, id -> new OrgBaseline(id, embeddingModel.dimension()));
        corpus.forEach(text -> baseline.update(embeddingModel.embed(text)));
        log.info("Bootstrapped baseline for org={} samples={}", orgId, corpus.size());
    }
}
