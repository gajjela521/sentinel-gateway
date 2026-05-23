package io.sentinelgateway.spring.dashboard;

import io.sentinelgateway.core.model.AuditEvent;
import io.sentinelgateway.core.model.SentinelDecision;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Bounded, thread-safe in-memory ring buffer for {@link AuditEvent} objects.
 * Default implementation of {@link AuditStore} — activated when no {@code DataSource}
 * bean is present (i.e., no database configured).
 *
 * <p>Retains the most recent {@value #MAX_SIZE} events. When capacity is exceeded
 * the oldest event is dropped. All aggregate counters are maintained incrementally
 * so stat reads are O(1).
 *
 * <p>Not suitable as a durable audit log. For production deployments configure a
 * {@code DataSource} to activate the JDBC-backed store instead.
 */
public final class AuditEventStore implements AuditStore {

    private static final int MAX_SIZE = 10_000;

    private final Deque<AuditEvent> ring = new ArrayDeque<>(MAX_SIZE);
    private final ReentrantLock lock = new ReentrantLock();

    private long totalRequests;
    private long allowedCount;
    private long blockedCount;
    private long flaggedCount;
    private long sumLatencyNanos;

    @Override
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

    @Override
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

        int total      = filtered.size();
        int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / size);
        int from       = Math.min(page * size, total);
        int to         = Math.min(from + size, total);

        return new AuditPage(filtered.subList(from, to), total, totalPages, page, size);
    }

    @Override
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

    @Override
    public Map<String, Long> topBlockedRules(int limit) {
        List<AuditEvent> snapshot;
        lock.lock();
        try { snapshot = new ArrayList<>(ring); }
        finally { lock.unlock(); }

        return snapshot.stream()
                .filter(e -> e.verdict() == SentinelDecision.Verdict.BLOCK
                          || e.verdict() == SentinelDecision.Verdict.QUARANTINE)
                .filter(e -> e.ruleId() != null)
                .collect(Collectors.groupingBy(AuditEvent::ruleId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    public int size() {
        lock.lock();
        try { return ring.size(); }
        finally { lock.unlock(); }
    }
}
