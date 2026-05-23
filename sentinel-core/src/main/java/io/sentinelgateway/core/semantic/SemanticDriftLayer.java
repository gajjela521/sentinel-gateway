package io.sentinelgateway.core.semantic;

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
 * Layer 4: Semantic Drift Detection.
 *
 * Algorithm:
 *   driftScore = α*(1 - cosineSim) + β*mutationWeight + γ*schemaAnomalyScore
 *
 * Each org gets its own {@link OrgBaseline}. The baseline is updated on every
 * ALLOW decision so it continuously learns normal traffic patterns.
 */
public final class SemanticDriftLayer implements SentinelLayer {

    private static final Logger log = LoggerFactory.getLogger(SemanticDriftLayer.class);

    public record DriftConfig(
            double alpha,         // weight for cosine distance
            double beta,          // weight for mutation penalty
            double gamma,         // weight for schema anomaly score
            double threshold,     // score above this → QUARANTINE
            int minBaselineSamples, // don't flag until baseline is warm
            boolean updateBaselineOnAllow // learning mode
    ) {
        public static DriftConfig defaults() {
            return new DriftConfig(0.5, 0.3, 0.2, 0.65, 50, true);
        }
    }

    private final EmbeddingModel embeddingModel;
    private final DriftConfig config;
    private final Map<String, OrgBaseline> baselines = new ConcurrentHashMap<>();

    public SemanticDriftLayer(EmbeddingModel embeddingModel, DriftConfig config) {
        this.embeddingModel = embeddingModel;
        this.config = config;
    }

    @Override
    public String name() { return "semantic_drift"; }

    @Override
    public LayerDecision evaluate(SentinelRequest request, PipelineContext ctx) {
        if (!embeddingModel.isAvailable()) {
            log.debug("Embedding model not available, skipping drift check for {}", request.requestId());
            return LayerDecision.pass();
        }

        float[] embedding = embeddingModel.embed(request.toEmbeddingText());
        ctx.put(PipelineContext.KEY_EMBEDDING, embedding);

        OrgBaseline baseline = baselines.computeIfAbsent(
                request.organizationId(),
                id -> new OrgBaseline(id, embeddingModel.dimension())
        );

        double cosineSim = baseline.cosineSimilarity(embedding, config.minBaselineSamples());
        double mutationWeight = request.method().isMutation() ? 1.0 : 0.0;

        Double schemaAnomaly = ctx.get(PipelineContext.KEY_SCHEMA_ANOMALY);
        double schemaScore = schemaAnomaly != null ? schemaAnomaly : 0.0;

        double driftScore = config.alpha() * (1.0 - cosineSim)
                + config.beta() * mutationWeight
                + config.gamma() * schemaScore;

        ctx.put(PipelineContext.KEY_DRIFT_SCORE, driftScore);
        ctx.put(PipelineContext.KEY_MUTATION_WEIGHT, mutationWeight);

        log.debug("Drift score {:.4f} (cosine={:.4f}) for request {}",
                driftScore, cosineSim, request.requestId());

        if (driftScore > config.threshold()) {
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

    /** Pre-warm an org baseline from a corpus of known-good requests. */
    public void bootstrapBaseline(String orgId, List<String> corpus) {
        OrgBaseline baseline = baselines.computeIfAbsent(
                orgId, id -> new OrgBaseline(id, embeddingModel.dimension()));
        corpus.forEach(text -> baseline.update(embeddingModel.embed(text)));
        log.info("Bootstrapped baseline for org {} with {} samples", orgId, corpus.size());
    }
}
