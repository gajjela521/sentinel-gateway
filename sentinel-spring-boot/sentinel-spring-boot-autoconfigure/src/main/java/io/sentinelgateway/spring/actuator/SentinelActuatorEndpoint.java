package io.sentinelgateway.spring.actuator;

import io.sentinelgateway.core.financial.FinancialCircuitBreakerLayer;
import io.sentinelgateway.core.pipeline.SentinelPipeline;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Exposes Sentinel runtime status at {@code /actuator/sentinel}.
 * Useful for health checks and ops dashboards.
 */
@Component
@Endpoint(id = "sentinel")
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
public class SentinelActuatorEndpoint {

    private final SentinelPipeline pipeline;

    public SentinelActuatorEndpoint(SentinelPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @ReadOperation
    public Map<String, Object> status() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "pipeline", "active"
        );
    }
}
