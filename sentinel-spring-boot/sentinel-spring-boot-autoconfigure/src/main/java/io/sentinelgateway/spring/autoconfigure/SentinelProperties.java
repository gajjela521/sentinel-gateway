package io.sentinelgateway.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties bound from {@code application.yml} under the
 * {@code sentinel} key.
 *
 * Example:
 * <pre>
 * sentinel:
 *   enabled: true
 *   policy-file: classpath:sentinel-policies.yaml
 *   injection-scanner:
 *     enabled: true
 *     block-threshold: 0.75
 *   semantic-drift:
 *     enabled: false
 *   financial-circuit-breaker:
 *     enabled: true
 *     max-spend-usd: 10.0
 *     max-tokens-burned: 50000
 *     max-state-mutations: 5
 *     velocity-window-ms: 60000
 * </pre>
 */
@ConfigurationProperties(prefix = "sentinel")
public class SentinelProperties {

    private boolean enabled = true;
    private String policyFile;

    private InjectionScannerProps injectionScanner = new InjectionScannerProps();
    private SemanticDriftProps semanticDrift = new SemanticDriftProps();
    private FinancialCBProps financialCircuitBreaker = new FinancialCBProps();
    private AuditProps audit = new AuditProps();

    public boolean isEnabled()                              { return enabled; }
    public void setEnabled(boolean v)                       { enabled = v; }
    public String getPolicyFile()                           { return policyFile; }
    public void setPolicyFile(String v)                     { policyFile = v; }
    public InjectionScannerProps getInjectionScanner()      { return injectionScanner; }
    public void setInjectionScanner(InjectionScannerProps v){ injectionScanner = v; }
    public SemanticDriftProps getSemanticDrift()            { return semanticDrift; }
    public void setSemanticDrift(SemanticDriftProps v)      { semanticDrift = v; }
    public FinancialCBProps getFinancialCircuitBreaker()    { return financialCircuitBreaker; }
    public void setFinancialCircuitBreaker(FinancialCBProps v){ financialCircuitBreaker = v; }
    public AuditProps getAudit()                            { return audit; }
    public void setAudit(AuditProps v)                      { audit = v; }

    public static class InjectionScannerProps {
        private boolean enabled = true;
        private double blockThreshold = 0.75;
        private double flagThreshold  = 0.50;

        public boolean isEnabled()             { return enabled; }
        public void setEnabled(boolean v)      { enabled = v; }
        public double getBlockThreshold()      { return blockThreshold; }
        public void setBlockThreshold(double v){ blockThreshold = v; }
        public double getFlagThreshold()       { return flagThreshold; }
        public void setFlagThreshold(double v) { flagThreshold = v; }
    }

    public static class SemanticDriftProps {
        private boolean enabled = false; // opt-in; requires ONNX model
        private String modelPath;
        private String vocabPath;
        private double alpha = 0.5;
        private double beta  = 0.3;
        private double gamma = 0.2;
        private double threshold = 0.65;
        private int minBaselineSamples = 50;

        public boolean isEnabled()                   { return enabled; }
        public void setEnabled(boolean v)            { enabled = v; }
        public String getModelPath()                 { return modelPath; }
        public void setModelPath(String v)           { modelPath = v; }
        public String getVocabPath()                 { return vocabPath; }
        public void setVocabPath(String v)           { vocabPath = v; }
        public double getAlpha()                     { return alpha; }
        public void setAlpha(double v)               { alpha = v; }
        public double getBeta()                      { return beta; }
        public void setBeta(double v)                { beta = v; }
        public double getGamma()                     { return gamma; }
        public void setGamma(double v)               { gamma = v; }
        public double getThreshold()                 { return threshold; }
        public void setThreshold(double v)           { threshold = v; }
        public int getMinBaselineSamples()           { return minBaselineSamples; }
        public void setMinBaselineSamples(int v)     { minBaselineSamples = v; }
    }

    public static class FinancialCBProps {
        private boolean enabled = true;
        private double maxSpendUsd        = 10.0;
        private long   maxTokensBurned    = 50_000;
        private int    maxStateMutations  = 5;
        private long   velocityWindowMs   = 60_000;

        public boolean isEnabled()                  { return enabled; }
        public void setEnabled(boolean v)           { enabled = v; }
        public double getMaxSpendUsd()              { return maxSpendUsd; }
        public void setMaxSpendUsd(double v)        { maxSpendUsd = v; }
        public long getMaxTokensBurned()            { return maxTokensBurned; }
        public void setMaxTokensBurned(long v)      { maxTokensBurned = v; }
        public int getMaxStateMutations()           { return maxStateMutations; }
        public void setMaxStateMutations(int v)     { maxStateMutations = v; }
        public long getVelocityWindowMs()           { return velocityWindowMs; }
        public void setVelocityWindowMs(long v)     { velocityWindowMs = v; }
    }

    public static class AuditProps {
        private boolean logToConsole = true;
        private String otlpEndpoint; // optional OpenTelemetry collector endpoint

        public boolean isLogToConsole()             { return logToConsole; }
        public void setLogToConsole(boolean v)      { logToConsole = v; }
        public String getOtlpEndpoint()             { return otlpEndpoint; }
        public void setOtlpEndpoint(String v)       { otlpEndpoint = v; }
    }
}
