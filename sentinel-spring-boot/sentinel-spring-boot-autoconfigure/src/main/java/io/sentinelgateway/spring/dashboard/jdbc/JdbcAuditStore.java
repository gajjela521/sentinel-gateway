package io.sentinelgateway.spring.dashboard.jdbc;

import io.sentinelgateway.core.model.AuditEvent;
import io.sentinelgateway.core.model.HttpMethod;
import io.sentinelgateway.core.model.SentinelDecision;
import io.sentinelgateway.spring.dashboard.AuditStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Durable {@link AuditStore} implementation backed by any JDBC-compatible database.
 *
 * <p>The schema is created automatically at startup via {@code CREATE TABLE IF NOT EXISTS},
 * so no migration tool is required. Tested against H2 (embedded default), PostgreSQL, and MySQL.
 *
 * <p>Activated automatically when a {@code DataSource} bean is present. Add
 * {@code spring-boot-starter-jdbc} (or any JPA starter) and either H2 (embedded) or
 * your production JDBC driver to the application's dependencies.
 *
 * <p>H2 file-based example in {@code application.yml}:
 * <pre>
 * spring:
 *   datasource:
 *     url: jdbc:h2:file:./sentinel-audit;AUTO_SERVER=TRUE
 *     driver-class-name: org.h2.Driver
 * </pre>
 */
public final class JdbcAuditStore implements AuditStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcAuditStore.class);

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS sentinel_audit_events (
                trace_id              VARCHAR(64)   PRIMARY KEY,
                request_id            VARCHAR(64),
                agent_id              VARCHAR(256),
                organization_id       VARCHAR(256),
                execution_thread_id   VARCHAR(256),
                method                VARCHAR(16),
                endpoint              VARCHAR(2048),
                verdict               VARCHAR(32),
                deciding_layer        VARCHAR(64),
                rule_id               VARCHAR(256),
                reason                VARCHAR(2048),
                timestamp             TIMESTAMP     NOT NULL,
                processing_nanos      BIGINT,
                drift_score           DOUBLE,
                injection_score       DOUBLE,
                schema_anomaly_score  DOUBLE,
                budget_consumed_usd   DOUBLE,
                mutations_in_window   INTEGER
            )
            """;

    private static final String INSERT = """
            INSERT INTO sentinel_audit_events
              (trace_id, request_id, agent_id, organization_id, execution_thread_id,
               method, endpoint, verdict, deciding_layer, rule_id, reason,
               timestamp, processing_nanos,
               drift_score, injection_score, schema_anomaly_score,
               budget_consumed_usd, mutations_in_window)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (trace_id) DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public JdbcAuditStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc must not be null");
        jdbc.execute(CREATE_TABLE);
        log.info("JdbcAuditStore initialized — audit events will be persisted to the configured DataSource");
    }

    @Override
    public void record(AuditEvent e) {
        if (e == null) return;
        try {
            AuditEvent.ScoreSnapshot s = e.scores();
            jdbc.update(INSERT,
                    e.traceId(), e.requestId(), e.agentId(), e.organizationId(), e.executionThreadId(),
                    e.method() != null ? e.method().name() : null,
                    e.endpoint(),
                    e.verdict() != null ? e.verdict().name() : null,
                    e.decidingLayer(), e.ruleId(), e.reason(),
                    Timestamp.from(e.timestamp() != null ? e.timestamp() : Instant.now()),
                    e.processingNanos(),
                    s != null ? s.driftScore()         : 0.0,
                    s != null ? s.injectionScore()     : 0.0,
                    s != null ? s.schemaAnomalyScore() : 0.0,
                    s != null ? s.budgetConsumedUsd()  : 0.0,
                    s != null ? s.mutationsInWindow()  : 0
            );
        } catch (Exception ex) {
            log.warn("Failed to persist audit event traceId={}: {}", e.traceId(), ex.getMessage());
        }
    }

    @Override
    public AuditPage page(int page, int size, SentinelDecision.Verdict verdict, String orgId) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");

        if (verdict != null) {
            where.append(" AND verdict = ?");
            params.add(verdict.name());
        }
        if (orgId != null && !orgId.isBlank()) {
            where.append(" AND organization_id = ?");
            params.add(orgId);
        }

        String countSql = "SELECT COUNT(*) FROM sentinel_audit_events" + where;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        long totalElements = total != null ? total : 0L;
        int  totalPages    = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / size);

        String dataSql = "SELECT * FROM sentinel_audit_events" + where
                + " ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add((long) page * size);

        List<AuditEvent> content = jdbc.query(dataSql, ROW_MAPPER, dataParams.toArray());
        return new AuditPage(content, totalElements, totalPages, page, size);
    }

    @Override
    public StatsSnapshot stats() {
        String sql = """
                SELECT
                  COUNT(*) AS total,
                  SUM(CASE WHEN verdict = 'ALLOW' THEN 1 ELSE 0 END) AS allowed,
                  SUM(CASE WHEN verdict IN ('BLOCK','QUARANTINE') THEN 1 ELSE 0 END) AS blocked,
                  SUM(CASE WHEN verdict = 'REQUIRE_APPROVAL' THEN 1 ELSE 0 END) AS flagged,
                  AVG(processing_nanos) AS avg_nanos
                FROM sentinel_audit_events
                """;
        return jdbc.queryForObject(sql, (rs, i) -> {
            double avgMs = rs.getDouble("avg_nanos") / 1_000_000.0;
            return new StatsSnapshot(
                    rs.getLong("total"), rs.getLong("allowed"),
                    rs.getLong("blocked"), rs.getLong("flagged"), avgMs);
        });
    }

    @Override
    public Map<String, Long> topBlockedRules(int limit) {
        String sql = """
                SELECT rule_id, COUNT(*) AS cnt
                FROM sentinel_audit_events
                WHERE verdict IN ('BLOCK','QUARANTINE') AND rule_id IS NOT NULL
                GROUP BY rule_id
                ORDER BY cnt DESC
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, i) -> Map.entry(rs.getString("rule_id"), rs.getLong("cnt")), limit)
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private static final RowMapper<AuditEvent> ROW_MAPPER = (ResultSet rs, int i) -> {
        Timestamp ts = rs.getTimestamp("timestamp");
        String methodStr  = rs.getString("method");
        String verdictStr = rs.getString("verdict");
        String scoreNull  = null;

        return new AuditEvent(
                rs.getString("trace_id"),
                rs.getString("request_id"),
                rs.getString("agent_id"),
                rs.getString("organization_id"),
                rs.getString("execution_thread_id"),
                methodStr  != null ? HttpMethod.valueOf(methodStr)               : null,
                rs.getString("endpoint"),
                verdictStr != null ? SentinelDecision.Verdict.valueOf(verdictStr) : null,
                rs.getString("deciding_layer"),
                rs.getString("rule_id"),
                rs.getString("reason"),
                ts != null ? ts.toInstant() : Instant.now(),
                rs.getLong("processing_nanos"),
                new AuditEvent.ScoreSnapshot(
                        rs.getDouble("drift_score"),
                        rs.getDouble("injection_score"),
                        rs.getDouble("schema_anomaly_score"),
                        rs.getDouble("budget_consumed_usd"),
                        rs.getInt("mutations_in_window")
                )
        );
    };
}
