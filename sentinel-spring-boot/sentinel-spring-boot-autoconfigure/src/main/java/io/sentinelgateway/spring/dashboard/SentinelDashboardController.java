package io.sentinelgateway.spring.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.sentinelgateway.core.financial.FinancialCircuitBreakerLayer;
import io.sentinelgateway.core.model.HttpMethod;
import io.sentinelgateway.core.model.SentinelDecision;
import io.sentinelgateway.core.policy.PolicyRule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API for the Sentinel Control Plane Dashboard.
 *
 * <p>All endpoints live under {@code /sentinel/} to avoid collisions with
 * the application's own routes. CORS is enabled for all origins so the React
 * SPA can call this API regardless of the port it's served on.
 *
 * <p>Authentication is intentionally omitted from this reference implementation;
 * production deployments should add Spring Security in front of these endpoints.
 */
@RestController
@RequestMapping("/sentinel")
@CrossOrigin(origins = "*")
public class SentinelDashboardController {

    private final PolicyStore policyStore;
    private final AuditEventStore auditStore;
    private final FinancialCircuitBreakerLayer circuitBreaker;

    public SentinelDashboardController(PolicyStore policyStore,
                                        AuditEventStore auditStore,
                                        FinancialCircuitBreakerLayer circuitBreaker) {
        this.policyStore   = Objects.requireNonNull(policyStore);
        this.auditStore    = Objects.requireNonNull(auditStore);
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker);
    }

    // ── Policy Endpoints ──────────────────────────────────────────────────────

    @GetMapping("/policies")
    public List<PolicyRuleDto> listPolicies() {
        return policyStore.list().stream().map(PolicyRuleDto::from).toList();
    }

    @GetMapping("/policies/{id}")
    public ResponseEntity<PolicyRuleDto> getPolicy(@PathVariable String id) {
        return policyStore.get(id)
                .map(r -> ResponseEntity.ok(PolicyRuleDto.from(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyRuleDto createPolicy(@RequestBody PolicyRuleDto dto) {
        PolicyRule created = policyStore.create(dto.toDomain(null));
        return PolicyRuleDto.from(created);
    }

    @PutMapping("/policies/{id}")
    public ResponseEntity<PolicyRuleDto> updatePolicy(@PathVariable String id,
                                                       @RequestBody PolicyRuleDto dto) {
        if (!policyStore.get(id).isPresent()) return ResponseEntity.notFound().build();
        PolicyRule updated = policyStore.update(id, dto.toDomain(id));
        return ResponseEntity.ok(PolicyRuleDto.from(updated));
    }

    @DeleteMapping("/policies/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String id) {
        return policyStore.delete(id)
                ? ResponseEntity.noContent().<Void>build()
                : ResponseEntity.notFound().<Void>build();
    }

    // ── Audit Log Endpoints ───────────────────────────────────────────────────

    @GetMapping("/audit")
    public AuditEventStore.AuditPage listAudit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String organizationId) {

        SentinelDecision.Verdict v = null;
        if (verdict != null && !verdict.isBlank()) {
            try { v = SentinelDecision.Verdict.valueOf(verdict.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { /* unknown verdict treated as no filter */ }
        }
        int clampedSize = Math.max(1, Math.min(size, 200));
        return auditStore.page(Math.max(0, page), clampedSize, v, organizationId);
    }

    // ── Budget Endpoints ──────────────────────────────────────────────────────

    @GetMapping("/budgets")
    public List<FinancialCircuitBreakerLayer.ThreadBudgetSnapshot> listBudgets() {
        return circuitBreaker.listSnapshots();
    }

    // ── Stats Endpoint ────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public StatsResponse stats() {
        AuditEventStore.StatsSnapshot s = auditStore.stats();

        Map<String, Long> blocksByRule = new LinkedHashMap<>();
        auditStore.page(0, 200, SentinelDecision.Verdict.BLOCK, null)
                .content().stream()
                .filter(e -> e.ruleId() != null)
                .forEach(e -> blocksByRule.merge(e.ruleId(), 1L, Long::sum));

        List<RuleCount> top = blocksByRule.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new RuleCount(e.getKey(), e.getValue()))
                .toList();

        return new StatsResponse(s.totalRequests(), s.allowedCount(), s.blockedCount(),
                s.flaggedCount(), s.avgLatencyMs(), top);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record PolicyRuleDto(
            String id,
            String description,
            @JsonProperty("methods") List<String> methods,
            @JsonProperty("endpointPattern") String endpointPattern,
            String action,
            int priority,
            String agentGuidance,
            List<String> safeAlternatives
    ) {
        static PolicyRuleDto from(PolicyRule r) {
            return new PolicyRuleDto(
                    r.id(), r.description(),
                    r.methods().stream().map(Enum::name).sorted().toList(),
                    r.endpointPattern() != null ? r.endpointPattern().pattern() : null,
                    r.action().name(), r.priority(),
                    r.agentGuidance(), r.safeAlternatives()
            );
        }

        PolicyRule toDomain(String forcedId) {
            PolicyRule.Builder b = PolicyRule.builder()
                    .id(forcedId != null ? forcedId : (id != null ? id : UUID.randomUUID().toString()))
                    .description(description != null ? description : "")
                    .methods(methods == null ? Set.of()
                            : methods.stream().map(HttpMethod::valueOf).collect(Collectors.toSet()))
                    .action(action != null ? PolicyRule.Action.valueOf(action) : PolicyRule.Action.BLOCK)
                    .priority(priority)
                    .agentGuidance(agentGuidance)
                    .safeAlternatives(safeAlternatives != null ? safeAlternatives : List.of());
            if (endpointPattern != null && !endpointPattern.isBlank()) {
                b.endpointPattern(endpointPattern);
            } else {
                b.endpointPattern(".*");
            }
            return b.build();
        }
    }

    public record StatsResponse(
            long totalRequests, long allowedCount, long blockedCount, long flaggedCount,
            double avgLatencyMs,
            @JsonProperty("topBlockedRules") List<RuleCount> topBlockedRules
    ) {}

    public record RuleCount(@JsonProperty("ruleId") String ruleId, long count) {}
}
