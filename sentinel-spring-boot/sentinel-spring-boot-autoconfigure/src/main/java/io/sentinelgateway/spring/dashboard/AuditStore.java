package io.sentinelgateway.spring.dashboard;

import io.sentinelgateway.core.model.AuditEvent;
import io.sentinelgateway.core.model.SentinelDecision;

import java.util.List;
import java.util.Map;

/**
 * Contract for audit event persistence. Two implementations are provided:
 * <ul>
 *   <li>{@link AuditEventStore} — in-memory ring buffer (default, no setup required)
 *   <li>{@code JdbcAuditStore} — durable JDBC-backed store, activated when a
 *       {@code DataSource} bean is present on the classpath
 * </ul>
 */
public interface AuditStore {

    /** Records one audit event. Must never throw — pipeline continuity takes priority. */
    void record(AuditEvent event);

    /**
     * Returns a paginated view of events, optionally filtered by verdict and organization.
     *
     * @param page    zero-based page index
     * @param size    page size (clamped by implementations to a safe maximum)
     * @param verdict optional filter; {@code null} = all verdicts
     * @param orgId   optional filter; {@code null} or blank = all organizations
     */
    AuditPage page(int page, int size, SentinelDecision.Verdict verdict, String orgId);

    /** Returns aggregate totals since the store was created or the DB was populated. */
    StatsSnapshot stats();

    /**
     * Returns the top N most-blocked rule IDs with their block counts, sorted descending.
     *
     * @param limit maximum number of entries to return
     */
    Map<String, Long> topBlockedRules(int limit);

    record AuditPage(
            List<AuditEvent> content,
            long totalElements,
            int totalPages,
            int number,
            int size
    ) {}

    record StatsSnapshot(
            long totalRequests,
            long allowedCount,
            long blockedCount,
            long flaggedCount,
            double avgLatencyMs
    ) {}
}
