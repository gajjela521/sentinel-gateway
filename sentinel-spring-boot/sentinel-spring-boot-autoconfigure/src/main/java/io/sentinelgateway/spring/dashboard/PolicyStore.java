package io.sentinelgateway.spring.dashboard;

import io.sentinelgateway.core.policy.PolicyRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory store for {@link PolicyRule} objects.
 *
 * <p>Rules are keyed by their {@link PolicyRule#id()} field. On creation, if no
 * id is supplied, a new UUID is generated. All read operations return defensive copies
 * so callers cannot mutate the store's internal state.
 */
public final class PolicyStore {

    private final ConcurrentHashMap<String, PolicyRule> rules = new ConcurrentHashMap<>();

    /**
     * Seeds the store with the given list of rules (e.g., from YAML bootstrap).
     * Any existing rules with the same id are replaced.
     */
    public void seed(List<PolicyRule> initial) {
        Objects.requireNonNull(initial, "initial must not be null");
        initial.forEach(r -> rules.put(r.id(), r));
    }

    /**
     * Returns all rules sorted by priority ascending.
     */
    public List<PolicyRule> list() {
        return new ArrayList<>(rules.values()).stream()
                .sorted(Comparator.comparingInt(PolicyRule::priority))
                .toList();
    }

    public Optional<PolicyRule> get(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(rules.get(id));
    }

    /**
     * Saves a new rule, assigning a UUID id if none is set.
     *
     * @return the persisted rule (with id populated)
     */
    public PolicyRule create(PolicyRule rule) {
        Objects.requireNonNull(rule, "rule must not be null");
        PolicyRule toStore = rule.id() == null || rule.id().isBlank()
                ? withId(rule, UUID.randomUUID().toString())
                : rule;
        if (rules.putIfAbsent(toStore.id(), toStore) != null) {
            throw new IllegalArgumentException("Rule with id '" + toStore.id() + "' already exists");
        }
        return toStore;
    }

    /**
     * Replaces the rule with the given id, or creates it if absent.
     *
     * @throws IllegalArgumentException if the body id does not match the path id
     */
    public PolicyRule update(String id, PolicyRule rule) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(rule, "rule must not be null");
        if (rule.id() != null && !rule.id().equals(id)) {
            throw new IllegalArgumentException(
                    "Rule body id '" + rule.id() + "' does not match path id '" + id + "'");
        }
        PolicyRule toStore = withId(rule, id);
        rules.put(id, toStore);
        return toStore;
    }

    /**
     * Removes the rule with the given id.
     *
     * @return {@code true} if it existed and was removed
     */
    public boolean delete(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return rules.remove(id) != null;
    }

    private static PolicyRule withId(PolicyRule r, String id) {
        return PolicyRule.builder()
                .id(id)
                .description(r.description())
                .methods(r.methods())
                .endpointPattern(r.endpointPattern() != null ? r.endpointPattern().pattern() : null)
                .action(r.action())
                .priority(r.priority())
                .agentGuidance(r.agentGuidance())
                .safeAlternatives(r.safeAlternatives())
                .build();
    }
}
