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
 * <p>All endpoints live under {@code /sentinel/} to avoid collisions with the
 * application's own routes. CORS is open so the React SPA can talk to the API
 * regardless of the port it is served on.
 *
 * <p>Authentication is intentionally omitted from this reference implementation;
 * production deployments should place Spring Security in front of these endpoints.
 */
@RestController
@RequestMapping("/sentinel")
@CrossOrigin(origins = "*")
public class SentinelDashboardController {

    private final PolicyStore policyStore;
    private final AuditStore auditStore;
    private final FinancialCircuitBreakerLayer circuitBreaker;

    public SentinelDashboardController(PolicyStore policyStore,
                                        AuditStore auditStore,
                                        FinancialCircuitBreakerLayer circuitBreaker) {
        this.policyStore    = Objects.requireNonNull(policyStore);
        this.auditStore     = Objects.requireNonNull(auditStore);
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
        return PolicyRuleDto.from(policyStore.create(dto.toDomain(null)));
    }

    @PutMapping("/policies/{id}")
    public ResponseEntity<PolicyRuleDto> updatePolicy(@PathVariable String id,
                                                       @RequestBody PolicyRuleDto dto) {
        if (policyStore.get(id).isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(PolicyRuleDto.from(policyStore.update(id, dto.toDomain(id))));
    }

    @DeleteMapping("/policies/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable String id) {
        return policyStore.delete(id)
                ? ResponseEntity.noContent().<Void>build()
                : ResponseEntity.notFound().<Void>build();
    }

    // ── Audit Log Endpoints ───────────────────────────────────────────────────

    @GetMapping("/audit")
    public AuditStore.AuditPage listAudit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) String organizationId) {

        SentinelDecision.Verdict v = null;
        if (verdict != null && !verdict.isBlank()) {
            try { v = SentinelDecision.Verdict.valueOf(verdict.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) {}
        }
        return auditStore.page(Math.max(0, page), Math.max(1, Math.min(size, 200)), v, organizationId);
    }

    // ── Budget Endpoints ──────────────────────────────────────────────────────

    @GetMapping("/budgets")
    public List<FinancialCircuitBreakerLayer.ThreadBudgetSnapshot> listBudgets() {
        return circuitBreaker.listSnapshots();
    }

    // ── Stats Endpoint ────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public StatsResponse stats() {
        AuditStore.StatsSnapshot s = auditStore.stats();
        List<RuleCount> top = auditStore.topBlockedRules(5).entrySet().stream()
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
            b.endpointPattern(endpointPattern != null && !endpointPattern.isBlank()
                    ? endpointPattern : ".*");
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
