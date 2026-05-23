package io.sentinelgateway.core.pipeline;

import io.sentinelgateway.core.financial.BudgetConfig;
import io.sentinelgateway.core.financial.FinancialCircuitBreakerLayer;
import io.sentinelgateway.core.injection.InjectionPatternLibrary;
import io.sentinelgateway.core.injection.PromptInjectionLayer;
import io.sentinelgateway.core.model.AuditEvent;
import io.sentinelgateway.core.policy.PolicyEngine;
import io.sentinelgateway.core.policy.PolicyRule;
import io.sentinelgateway.core.semantic.EmbeddingModel;
import io.sentinelgateway.core.semantic.NoOpEmbeddingModel;
import io.sentinelgateway.core.semantic.SemanticDriftLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fluent builder for assembling the Sentinel processing pipeline.
 *
 * <p>All setter methods validate their inputs immediately so misconfiguration is
 * caught at build time rather than at request-evaluation time.
 *
 * <p>Default layer order:
 * <ol>
 *   <li>Policy Engine — YAML rule evaluation (if any rules are configured)
 *   <li>Injection Scanner — prompt injection pattern detection
 *   <li>Semantic Drift — embedding-based behavioral drift (requires ONNX model)
 *   <li>Financial Circuit Breaker — per-thread budget and mutation velocity enforcement
 *   <li>Custom Layers — caller-supplied extensions, appended in registration order
 * </ol>
 *
 * <p>At least one layer must be enabled; {@link #build()} throws if all layers are disabled.
 */
public final class SentinelPipelineBuilder {

    private List<PolicyRule> policyRules           = List.of();
    private boolean injectionScannerEnabled        = true;
    private double  injectionBlockThreshold        = 0.75;
    private double  injectionFlagThreshold         = 0.50;
    private EmbeddingModel embeddingModel          = NoOpEmbeddingModel.INSTANCE;
    private SemanticDriftLayer.DriftConfig driftConfig = SemanticDriftLayer.DriftConfig.defaults();
    private boolean semanticDriftEnabled           = true;
    private BudgetConfig budgetConfig              = BudgetConfig.defaults();
    private boolean financialCBEnabled             = true;
    private final List<SentinelLayer> customLayers = new ArrayList<>();
    private Consumer<AuditEvent> auditSink;

    /**
     * Sets the policy rules to evaluate. An empty list disables the policy layer.
     *
     * @param rules must not be null
     */
    public SentinelPipelineBuilder policyRules(List<PolicyRule> rules) {
        this.policyRules = List.copyOf(Objects.requireNonNull(rules, "rules must not be null"));
        return this;
    }

    /** Enables or disables the prompt injection scanner layer. */
    public SentinelPipelineBuilder injectionScanner(boolean enabled) {
        this.injectionScannerEnabled = enabled;
        return this;
    }

    /**
     * Configures injection detection thresholds.
     *
     * @param block requests scoring at or above this value are blocked; range [0.0, 1.0]
     * @param flag  requests scoring at or above this value are flagged (non-blocking); range [0.0, 1.0]
     * @throws IllegalArgumentException if either value is out of range or {@code block < flag}
     */
    public SentinelPipelineBuilder injectionThresholds(double block, double flag) {
        if (block < 0.0 || block > 1.0)
            throw new IllegalArgumentException("block threshold must be in [0.0, 1.0], got " + block);
        if (flag < 0.0 || flag > 1.0)
            throw new IllegalArgumentException("flag threshold must be in [0.0, 1.0], got " + flag);
        if (block < flag)
            throw new IllegalArgumentException(
                    "block threshold must be >= flag threshold; got block=" + block + " flag=" + flag);
        this.injectionBlockThreshold = block;
        this.injectionFlagThreshold  = flag;
        return this;
    }

    /**
     * Sets the embedding model for semantic drift detection.
     * Defaults to {@link NoOpEmbeddingModel#INSTANCE} (drift checks skipped).
     *
     * @param model must not be null
     */
    public SentinelPipelineBuilder embeddingModel(EmbeddingModel model) {
        this.embeddingModel = Objects.requireNonNull(model, "embeddingModel must not be null");
        return this;
    }

    /**
     * Sets the drift detection configuration.
     *
     * @param config must not be null
     */
    public SentinelPipelineBuilder driftConfig(SemanticDriftLayer.DriftConfig config) {
        this.driftConfig = Objects.requireNonNull(config, "driftConfig must not be null");
        return this;
    }

    /** Enables or disables the semantic drift detection layer. */
    public SentinelPipelineBuilder semanticDrift(boolean enabled) {
        this.semanticDriftEnabled = enabled;
        return this;
    }

    /**
     * Sets the financial circuit breaker budget configuration.
     *
     * @param config must not be null
     */
    public SentinelPipelineBuilder budgetConfig(BudgetConfig config) {
        this.budgetConfig = Objects.requireNonNull(config, "budgetConfig must not be null");
        return this;
    }

    /** Enables or disables the financial circuit breaker layer. */
    public SentinelPipelineBuilder financialCircuitBreaker(boolean enabled) {
        this.financialCBEnabled = enabled;
        return this;
    }

    /**
     * Appends a custom layer at the end of the pipeline.
     *
     * @param layer must not be null
     */
    public SentinelPipelineBuilder customLayer(SentinelLayer layer) {
        this.customLayers.add(Objects.requireNonNull(layer, "customLayer must not be null"));
        return this;
    }

    /**
     * Sets the audit event sink. When set, every evaluated request produces an
     * {@link AuditEvent} delivered to this consumer regardless of verdict.
     *
     * @param sink must not be null
     */
    public SentinelPipelineBuilder auditSink(Consumer<AuditEvent> sink) {
        this.auditSink = Objects.requireNonNull(sink, "auditSink must not be null");
        return this;
    }

    /**
     * Builds the configured pipeline.
     *
     * @throws IllegalStateException if no layers are enabled
     */
    public SentinelPipeline build() {
        List<SentinelLayer> layers = new ArrayList<>();

        if (!policyRules.isEmpty()) {
            layers.add(new PolicyEngine(policyRules));
        }

        if (injectionScannerEnabled) {
            layers.add(new PromptInjectionLayer(
                    InjectionPatternLibrary.defaults(),
                    injectionBlockThreshold,
                    injectionFlagThreshold));
        }

        if (semanticDriftEnabled) {
            layers.add(new SemanticDriftLayer(embeddingModel, driftConfig));
        }

        if (financialCBEnabled) {
            layers.add(new FinancialCircuitBreakerLayer(budgetConfig));
        }

        layers.addAll(customLayers);

        if (layers.isEmpty()) {
            throw new IllegalStateException(
                    "Pipeline requires at least one enabled layer. Enable at least one of: " +
                    "policyRules, injectionScanner, semanticDrift, financialCircuitBreaker, or customLayer.");
        }

        return new SentinelPipeline(layers, auditSink);
    }
}
