package io.sentinelgateway.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * OpenTelemetry-compatible audit record written for every request regardless of outcome.
 * Designed to flow directly into SIEM systems (Splunk, Datadog, Elastic).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEvent(
        String traceId,
        String requestId,
        String agentId,
        String organizationId,
        String executionThreadId,
        HttpMethod method,
        String endpoint,
        SentinelDecision.Verdict verdict,
        String decidingLayer,
        String ruleId,
        String reason,
        Instant timestamp,
        long processingNanos,
        ScoreSnapshot scores
) {
    public record ScoreSnapshot(
            double driftScore,
            double injectionScore,
            double schemaAnomalyScore,
            double budgetConsumedUsd,
            int mutationsInWindow
    ) {}
}
