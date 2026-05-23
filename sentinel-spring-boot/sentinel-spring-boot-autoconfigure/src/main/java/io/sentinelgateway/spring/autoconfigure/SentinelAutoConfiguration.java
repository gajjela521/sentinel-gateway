package io.sentinelgateway.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentinelgateway.core.financial.BudgetConfig;
import io.sentinelgateway.core.model.AuditEvent;
import io.sentinelgateway.core.pipeline.SentinelPipeline;
import io.sentinelgateway.core.pipeline.SentinelPipelineBuilder;
import io.sentinelgateway.core.policy.PolicyLoader;
import io.sentinelgateway.core.policy.PolicyRule;
import io.sentinelgateway.core.semantic.NoOpEmbeddingModel;
import io.sentinelgateway.core.semantic.SemanticDriftLayer;
import io.sentinelgateway.spring.filter.SentinelFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

@AutoConfiguration
@EnableConfigurationProperties(SentinelProperties.class)
@ConditionalOnProperty(name = "sentinel.enabled", havingValue = "true", matchIfMissing = true)
public class SentinelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SentinelAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SentinelPipeline sentinelPipeline(SentinelProperties props, ResourceLoader resourceLoader) {
        SentinelPipelineBuilder builder = new SentinelPipelineBuilder();

        // Policy rules
        List<PolicyRule> rules = loadPolicies(props, resourceLoader);
        if (!rules.isEmpty()) builder.policyRules(rules);

        // Injection scanner
        SentinelProperties.InjectionScannerProps injection = props.getInjectionScanner();
        builder.injectionScanner(injection.isEnabled())
               .injectionThresholds(injection.getBlockThreshold(), injection.getFlagThreshold());

        // Semantic drift
        SentinelProperties.SemanticDriftProps drift = props.getSemanticDrift();
        builder.semanticDrift(drift.isEnabled());
        if (drift.isEnabled()) {
            builder.embeddingModel(NoOpEmbeddingModel.INSTANCE) // replaced by ONNX bean if model-path set
                   .driftConfig(new SemanticDriftLayer.DriftConfig(
                           drift.getAlpha(), drift.getBeta(), drift.getGamma(),
                           drift.getThreshold(), drift.getMinBaselineSamples(), true));
        }

        // Financial circuit breaker
        SentinelProperties.FinancialCBProps cb = props.getFinancialCircuitBreaker();
        builder.financialCircuitBreaker(cb.isEnabled());
        if (cb.isEnabled()) {
            builder.budgetConfig(new BudgetConfig(
                    cb.getMaxSpendUsd(), cb.getMaxTokensBurned(),
                    cb.getMaxStateMutations(), cb.getVelocityWindowMs()));
        }

        // Audit sink
        ObjectMapper mapper = new ObjectMapper();
        Consumer<AuditEvent> sink = buildAuditSink(props, mapper);
        builder.auditSink(sink);

        return builder.build();
    }

    @Bean
    public FilterRegistrationBean<SentinelFilter> sentinelFilterRegistration(SentinelPipeline pipeline) {
        FilterRegistrationBean<SentinelFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new SentinelFilter(pipeline));
        bean.addUrlPatterns("/*");
        bean.setOrder(1); // run before other filters
        bean.setName("sentinelFilter");
        return bean;
    }

    private List<PolicyRule> loadPolicies(SentinelProperties props, ResourceLoader loader) {
        String policyFile = props.getPolicyFile();
        if (policyFile == null || policyFile.isBlank()) {
            // Load bundled defaults
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

    private Consumer<AuditEvent> buildAuditSink(SentinelProperties props, ObjectMapper mapper) {
        if (!props.getAudit().isLogToConsole()) return event -> {};

        return event -> {
            try {
                log.info("SENTINEL_AUDIT {}", mapper.writeValueAsString(event));
            } catch (Exception e) {
                log.warn("Failed to serialize audit event", e);
            }
        };
    }
}
