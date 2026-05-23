package io.sentinelgateway.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinelgateway.core.financial.BudgetConfig;
import io.sentinelgateway.core.financial.FinancialCircuitBreakerLayer;
import io.sentinelgateway.core.injection.InjectionPatternLibrary;
import io.sentinelgateway.core.injection.PromptInjectionLayer;
import io.sentinelgateway.core.model.AuditEvent;
import io.sentinelgateway.core.pipeline.SentinelLayer;
import io.sentinelgateway.core.pipeline.SentinelPipeline;
import io.sentinelgateway.core.policy.PolicyLoader;
import io.sentinelgateway.core.policy.PolicyRule;
import io.sentinelgateway.core.semantic.EmbeddingModel;
import io.sentinelgateway.core.semantic.NoOpEmbeddingModel;
import io.sentinelgateway.core.semantic.OnnxEmbeddingModel;
import io.sentinelgateway.core.semantic.SemanticDriftLayer;
import io.sentinelgateway.spring.dashboard.AuditEventStore;
import io.sentinelgateway.spring.dashboard.AuditStore;
import io.sentinelgateway.spring.dashboard.LivePolicyLayer;
import io.sentinelgateway.spring.dashboard.PolicyStore;
import io.sentinelgateway.spring.dashboard.SentinelDashboardController;
import io.sentinelgateway.spring.dashboard.jdbc.JdbcAuditStore;
import io.sentinelgateway.spring.filter.SentinelFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@AutoConfiguration
@EnableConfigurationProperties(SentinelProperties.class)
@ConditionalOnProperty(name = "sentinel.enabled", havingValue = "true", matchIfMissing = true)
public class SentinelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SentinelAutoConfiguration.class);

    // ── Audit store: JDBC if DataSource present, in-memory otherwise ──────────

    @Bean
    @ConditionalOnMissingBean(AuditStore.class)
    @ConditionalOnClass(JdbcTemplate.class)
    public AuditStore sentinelJdbcAuditStore(JdbcTemplate jdbc) {
        return new JdbcAuditStore(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean(AuditStore.class)
    public AuditStore sentinelInMemoryAuditStore() {
        log.info("No DataSource detected — using in-memory audit store (last 10,000 events)");
        return new AuditEventStore();
    }

    // ── Dashboard infrastructure beans ────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public PolicyStore sentinelPolicyStore(SentinelProperties props, ResourceLoader resourceLoader) {
        PolicyStore store = new PolicyStore();
        List<PolicyRule> rules = loadPolicies(props, resourceLoader);
        if (!rules.isEmpty()) store.seed(rules);
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    public FinancialCircuitBreakerLayer sentinelFinancialCircuitBreaker(SentinelProperties props) {
        SentinelProperties.FinancialCBProps cb = props.getFinancialCircuitBreaker();
        BudgetConfig config = cb.isEnabled()
                ? new BudgetConfig(cb.getMaxSpendUsd(), cb.getMaxTokensBurned(),
                                   cb.getMaxStateMutations(), cb.getVelocityWindowMs())
                : BudgetConfig.defaults();
        return new FinancialCircuitBreakerLayer(config);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
    public SentinelDashboardController sentinelDashboardController(PolicyStore policyStore,
                                                                    AuditStore auditStore,
                                                                    FinancialCircuitBreakerLayer cb) {
        return new SentinelDashboardController(policyStore, auditStore, cb);
    }

    // ── Pipeline assembly ─────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    public SentinelPipeline sentinelPipeline(SentinelProperties props,
                                              PolicyStore policyStore,
                                              AuditStore auditStore,
                                              FinancialCircuitBreakerLayer circuitBreaker) {
        List<SentinelLayer> layers = new ArrayList<>();

        // Layer 1: Live policy engine (reads from PolicyStore on each request)
        layers.add(new LivePolicyLayer(policyStore));

        // Layer 2: Injection scanner
        SentinelProperties.InjectionScannerProps injection = props.getInjectionScanner();
        if (injection.isEnabled()) {
            layers.add(new PromptInjectionLayer(
                    InjectionPatternLibrary.defaults(),
                    injection.getBlockThreshold(),
                    injection.getFlagThreshold()));
        }

        // Layer 3: Semantic drift (with ONNX model if model-path is configured)
        SentinelProperties.SemanticDriftProps drift = props.getSemanticDrift();
        if (drift.isEnabled()) {
            EmbeddingModel model = resolveEmbeddingModel(drift);
            layers.add(new SemanticDriftLayer(
                    model,
                    new SemanticDriftLayer.DriftConfig(
                            drift.getAlpha(), drift.getBeta(), drift.getGamma(),
                            drift.getThreshold(), drift.getMinBaselineSamples(), true)));
        }

        // Layer 4: Financial circuit breaker
        if (props.getFinancialCircuitBreaker().isEnabled()) {
            layers.add(circuitBreaker);
        }

        // Guard: always ensure at least one layer
        if (layers.isEmpty()) {
            layers.add(new LivePolicyLayer(policyStore));
        }

        ObjectMapper mapper = new ObjectMapper();
        Consumer<AuditEvent> sink = buildAuditSink(props, mapper, auditStore);
        return new SentinelPipeline(layers, sink);
    }

    @Bean
    public FilterRegistrationBean<SentinelFilter> sentinelFilterRegistration(SentinelPipeline pipeline) {
        FilterRegistrationBean<SentinelFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new SentinelFilter(pipeline));
        bean.addUrlPatterns("/*");
        bean.setOrder(1);
        bean.setName("sentinelFilter");
        return bean;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns an ONNX embedding model when {@code sentinel.semantic-drift.model-path} points to
     * an existing file, otherwise falls back to the no-op implementation that always returns
     * zero vectors (drift detection effectively disabled).
     */
    private EmbeddingModel resolveEmbeddingModel(SentinelProperties.SemanticDriftProps drift) {
        String modelPathStr = drift.getModelPath();
        if (modelPathStr == null || modelPathStr.isBlank()) {
            log.info("sentinel.semantic-drift.model-path not set — using NoOp embedding model");
            return NoOpEmbeddingModel.INSTANCE;
        }

        Path modelPath = Path.of(modelPathStr);
        if (!Files.exists(modelPath)) {
            log.warn("ONNX model not found at '{}' — falling back to NoOp embedding model. " +
                     "Run scripts/download-model.sh to fetch the model.", modelPath);
            return NoOpEmbeddingModel.INSTANCE;
        }

        Path vocabPath = drift.getVocabPath() != null && !drift.getVocabPath().isBlank()
                ? Path.of(drift.getVocabPath())
                : modelPath.getParent().resolve("vocab.txt");

        try {
            OnnxEmbeddingModel model = new OnnxEmbeddingModel(modelPath, vocabPath);
            log.info("Loaded ONNX embedding model from '{}'", modelPath);
            return model;
        } catch (Exception e) {
            log.warn("Failed to load ONNX embedding model from '{}': {} — falling back to NoOp",
                    modelPath, e.getMessage());
            return NoOpEmbeddingModel.INSTANCE;
        }
    }

    private List<PolicyRule> loadPolicies(SentinelProperties props, ResourceLoader loader) {
        String policyFile = props.getPolicyFile();
        if (policyFile == null || policyFile.isBlank()) {
            try (InputStream is = getClass().getResourceAsStream("/sentinel-default-policies.yaml")) {
                if (is != null) return PolicyLoader.fromYaml(is);
            } catch (IOException e) {
                log.warn("Could not load bundled default policies", e);
            }
            return List.of();
        }
        try (InputStream is = loader.getResource(policyFile).getInputStream()) {
            return PolicyLoader.fromYaml(is);
        } catch (IOException e) {
            log.error("Failed to load policy file: {}", policyFile, e);
            return List.of();
        }
    }

    private Consumer<AuditEvent> buildAuditSink(SentinelProperties props,
                                                  ObjectMapper mapper,
                                                  AuditStore store) {
        return event -> {
            store.record(event);
            if (props.getAudit().isLogToConsole()) {
                try {
                    log.info("SENTINEL_AUDIT {}", mapper.writeValueAsString(event));
                } catch (Exception e) {
                    log.warn("Failed to serialize audit event", e);
                }
            }
        };
    }
}
