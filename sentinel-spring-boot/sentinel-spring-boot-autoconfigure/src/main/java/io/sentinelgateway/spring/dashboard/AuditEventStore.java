package io.sentinelgateway.spring.dashboard;

import io.sentinelgateway.core.model.AuditEvent;
import io.sentinelgateway.core.model.SentinelDecision;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded, thread-safe in-memory ring buffer for {@link AuditEvent} objects.
 *
 * <p>Intended for the control plane dashboard. Newest events are at the head;
 * when capacity is exceeded the oldest event is dropped.
 *
 * <p>Not a replacement for a durable audit store. For production use, wire
 * the audit sink to Kafka, a time-series DB, or an S3 append sink instead.
 */
public final class AuditEventStore {

    private static final int MAX_SIZE = 10_000;

    /** Newest-first ring buffer. */
    private final Deque<AuditEvent> ring = new ArrayDeque<>(MAX_SIZE);
    private final ReentrantLock lock = new ReentrantLock();

    // Running totals — updated under lock
    private long totalRequests;
    private long allowedCount;
    private long blockedCount;
    private long flaggedCount;
    private long sumLatencyNanos;

    /** Called by the audit sink on every request. */
    public void record(AuditEvent event) {
        if (event == null) return;
        lock.lock();
        try {
            ring.addFirst(event);
            if (ring.size() > MAX_SIZE) ring.removeLast();

            totalRequests++;
            sumLatencyNanos += event.processingNanos();
            switch (event.verdict()) {
                case ALLOW -> allowedCount++;
                case BLOCK, QUARANTINE -> blockedCount++;
                case REQUIRE_APPROVAL -> flaggedCount++;
            }
        } finally {
            lock.unlock();
        }
    }

    public record AuditPage(List<AuditEvent> content, long totalElements, int totalPages, int number, int size) {}

    /**
     * Returns a paginated view of recorded events, optionally filtered by verdict and/or organization.
     *
     * @param page    zero-based page index
     * @param size    number of events per page
     * @param verdict optional verdict filter (null = all)
     * @param orgId   optional organization filter (null = all)
     */
    public AuditPage page(int page, int size, SentinelDecision.Verdict verdict, String orgId) {
        List<AuditEvent> snapshot;
        lock.lock();
        try {
            snapshot = new ArrayList<>(ring);
        } finally {
            lock.unlock();
        }

        List<AuditEvent> filtered = snapshot.stream()
                .filter(e -> verdict == null || e.verdict() == verdict)
                .filter(e -> orgId == null || orgId.isBlank() || orgId.equals(e.organizationId()))
                .toList();

        int total = filtered.size();
        int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / size);
        int from = Math.min(page * size, total);
        int to   = Math.min(from + size, total);

        return new AuditPage(filtered.subList(from, to), total, totalPages, page, size);
    }

    public record StatsSnapshot(
            long totalRequests, long allowedCount, long blockedCount, long flaggedCount,
            double avgLatencyMs
    ) {}

    /** Returns aggregate statistics since startup. */
    public StatsSnapshot stats() {
        lock.lock();
        try {
            double avgMs = totalRequests == 0 ? 0.0
                    : sumLatencyNanos / (double) totalRequests / 1_000_000.0;
            return new StatsSnapshot(totalRequests, allowedCount, blockedCount, flaggedCount, avgMs);
        } finally {
            lock.unlock();
        }
    }

    /** Returns the current number of stored events. */
    public int size() {
        lock.lock();
        try { return ring.size(); }
        finally { lock.unlock(); }
    }
}
