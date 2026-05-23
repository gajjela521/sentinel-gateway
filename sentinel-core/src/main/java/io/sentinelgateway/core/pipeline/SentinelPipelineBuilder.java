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
import java.util.function.Consumer;

/**
 * Fluent builder for assembling the 6-layer pipeline with sensible defaults.
 *
 * Default layer order (each can be disabled):
 *   1. Policy Engine        — rule-based allow/block/require-approval
 *   2. Injection Scanner    — prompt injection detection
 *   3. Semantic Drift       — embedding-based behavioral drift
 *   4. Financial CB         — budget and mutation velocity
 *   5. Custom layers        — user-supplied extensions
 */
public final class SentinelPipelineBuilder {

    private List<PolicyRule> policyRules = List.of();
    private boolean injectionScannerEnabled = true;
    private double injectionBlockThreshold = 0.75;
    private double injectionFlagThreshold  = 0.50;
    private EmbeddingModel embeddingModel = NoOpEmbeddingModel.INSTANCE;
    private SemanticDriftLayer.DriftConfig driftConfig = SemanticDriftLayer.DriftConfig.defaults();
    private boolean semanticDriftEnabled = true;
    private BudgetConfig budgetConfig = BudgetConfig.defaults();
    private boolean financialCBEnabled = true;
    private final List<SentinelLayer> customLayers = new ArrayList<>();
    private Consumer<AuditEvent> auditSink;

    public SentinelPipelineBuilder policyRules(List<PolicyRule> rules) {
        this.policyRules = rules;
        return this;
    }

    public SentinelPipelineBuilder injectionScanner(boolean enabled) {
        this.injectionScannerEnabled = enabled;
        return this;
    }

    public SentinelPipelineBuilder injectionThresholds(double block, double flag) {
        this.injectionBlockThreshold = block;
        this.injectionFlagThreshold = flag;
        return this;
    }

    public SentinelPipelineBuilder embeddingModel(EmbeddingModel model) {
        this.embeddingModel = model;
        return this;
    }

    public SentinelPipelineBuilder driftConfig(SemanticDriftLayer.DriftConfig config) {
        this.driftConfig = config;
        return this;
    }

    public SentinelPipelineBuilder semanticDrift(boolean enabled) {
        this.semanticDriftEnabled = enabled;
        return this;
    }

    public SentinelPipelineBuilder budgetConfig(BudgetConfig config) {
        this.budgetConfig = config;
        return this;
    }

    public SentinelPipelineBuilder financialCircuitBreaker(boolean enabled) {
        this.financialCBEnabled = enabled;
        return this;
    }

    public SentinelPipelineBuilder customLayer(SentinelLayer layer) {
        this.customLayers.add(layer);
        return this;
    }

    public SentinelPipelineBuilder auditSink(Consumer<AuditEvent> sink) {
        this.auditSink = sink;
        return this;
    }

    public SentinelPipeline build() {
        List<SentinelLayer> layers = new ArrayList<>();

        // Layer 1: Policy engine
        if (!policyRules.isEmpty()) {
            layers.add(new PolicyEngine(policyRules));
        }

        // Layer 2: Injection scanner
        if (injectionScannerEnabled) {
            layers.add(new PromptInjectionLayer(
                    InjectionPatternLibrary.defaults(),
                    injectionBlockThreshold,
                    injectionFlagThreshold));
        }

        // Layer 3: Semantic drift
        if (semanticDriftEnabled) {
            layers.add(new SemanticDriftLayer(embeddingModel, driftConfig));
        }

        // Layer 4: Financial circuit breaker
        if (financialCBEnabled) {
            layers.add(new FinancialCircuitBreakerLayer(budgetConfig));
        }

        // Layer 5+: Custom user layers
        layers.addAll(customLayers);

        if (layers.isEmpty()) {
            throw new IllegalStateException("Pipeline must have at least one layer enabled");
        }

        return new SentinelPipeline(layers, auditSink);
    }
}
