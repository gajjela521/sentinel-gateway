package io.sentinelgateway.core.policy;

import io.sentinelgateway.core.model.HttpMethod;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * YAML-native policy rule. Compiles to an evaluatable predicate at load time
 * so hot-path evaluation is pure in-memory logic with no I/O.
 */
public final class PolicyRule {

    public enum Action { ALLOW, BLOCK, QUARANTINE, REQUIRE_APPROVAL }

    private final String id;
    private final String description;
    private final Set<HttpMethod> methods;
    private final Pattern endpointPattern;
    private final Integer minResourceCount;  // e.g. "block bulk deletes of >1"
    private final Action action;
    private final String agentGuidance;
    private final List<String> safeAlternatives;
    private final int priority;              // lower number = evaluated first

    private PolicyRule(Builder b) {
        this.id = b.id;
        this.description = b.description;
        this.methods = b.methods != null ? Set.copyOf(b.methods) : Set.of();
        this.endpointPattern = b.endpointPattern;
        this.minResourceCount = b.minResourceCount;
        this.action = b.action;
        this.agentGuidance = b.agentGuidance;
        this.safeAlternatives = b.safeAlternatives != null ? List.copyOf(b.safeAlternatives) : List.of();
        this.priority = b.priority;
    }

    public String id()                        { return id; }
    public String description()               { return description; }
    public Set<HttpMethod> methods()          { return methods; }
    public Pattern endpointPattern()          { return endpointPattern; }
    public Integer minResourceCount()         { return minResourceCount; }
    public Action action()                    { return action; }
    public String agentGuidance()             { return agentGuidance; }
    public List<String> safeAlternatives()    { return safeAlternatives; }
    public int priority()                     { return priority; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String description;
        private Set<HttpMethod> methods;
        private Pattern endpointPattern;
        private Integer minResourceCount;
        private Action action = Action.BLOCK;
        private String agentGuidance;
        private List<String> safeAlternatives;
        private int priority = 100;

        public Builder id(String v)                     { id = v; return this; }
        public Builder description(String v)            { description = v; return this; }
        public Builder methods(Set<HttpMethod> v)       { methods = v; return this; }
        public Builder endpointPattern(String regex)    { endpointPattern = Pattern.compile(regex); return this; }
        public Builder minResourceCount(int v)          { minResourceCount = v; return this; }
        public Builder action(Action v)                 { action = v; return this; }
        public Builder agentGuidance(String v)          { agentGuidance = v; return this; }
        public Builder safeAlternatives(List<String> v) { safeAlternatives = v; return this; }
        public Builder priority(int v)                  { priority = v; return this; }

        public PolicyRule build() {
            if (id == null) throw new IllegalStateException("rule id required");
            return new PolicyRule(this);
        }
    }
}
