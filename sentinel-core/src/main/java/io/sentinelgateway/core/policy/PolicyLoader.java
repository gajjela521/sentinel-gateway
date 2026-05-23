package io.sentinelgateway.core.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.sentinelgateway.core.model.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Loads policy rules from YAML. See sentinel-default-policies.yaml for the schema. */
public final class PolicyLoader {

    private static final Logger log = LoggerFactory.getLogger(PolicyLoader.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private PolicyLoader() {}

    /**
     * Parses all rules from the given YAML input stream.
     * Malformed individual rules are skipped with a warning; the stream is never closed by this method.
     *
     * @param input YAML content; must not be null
     * @return immutable list of successfully parsed rules; never null
     * @throws IOException if the stream cannot be read
     */
    @SuppressWarnings("unchecked")
    public static List<PolicyRule> fromYaml(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input must not be null");

        Map<String, Object> root = YAML.readValue(input, Map.class);
        Object rulesObj = root.get("rules");
        if (rulesObj == null) {
            log.info("No 'rules' key found in policy YAML; returning empty rule set");
            return List.of();
        }
        if (!(rulesObj instanceof List<?> rawRules)) {
            throw new IOException("'rules' must be a YAML list, got " + rulesObj.getClass().getSimpleName());
        }

        List<PolicyRule> result = new ArrayList<>(rawRules.size());
        for (Object rawEntry : rawRules) {
            if (!(rawEntry instanceof Map)) {
                log.warn("Skipping non-map rule entry: {}", rawEntry);
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) rawEntry;
            try {
                result.add(parseRule(raw));
            } catch (IllegalArgumentException | ClassCastException e) {
                log.warn("Skipping malformed rule id={}: {}", raw.get("id"), e.getMessage(), e);
            }
        }

        log.info("Loaded {} policy rules from YAML", result.size());
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static PolicyRule parseRule(Map<String, Object> raw) {
        String id = requireString(raw, "id");
        String description = optString(raw, "description", "");
        String actionStr  = optString(raw, "action", "BLOCK").toUpperCase();
        PolicyRule.Action action;
        try {
            action = PolicyRule.Action.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown action '" + actionStr + "' in rule '" + id + "'. Valid: ALLOW, BLOCK, QUARANTINE, REQUIRE_APPROVAL");
        }

        String agentGuidance = optString(raw, "agent_guidance", null);
        int priority = optInt(raw, "priority", 100);

        PolicyRule.Builder b = PolicyRule.builder()
                .id(id)
                .description(description)
                .action(action)
                .agentGuidance(agentGuidance)
                .priority(priority);

        Object rawMethods = raw.get("methods");
        if (rawMethods instanceof List<?> methodList) {
            Set<HttpMethod> methods = methodList.stream()
                    .filter(m -> m instanceof String)
                    .map(m -> {
                        try {
                            return HttpMethod.valueOf(((String) m).toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException(
                                    "Unknown HTTP method '" + m + "' in rule '" + id + "'");
                        }
                    })
                    .collect(Collectors.toUnmodifiableSet());
            b.methods(methods);
        }

        String pattern = optString(raw, "endpoint_pattern", null);
        if (pattern != null) b.endpointPattern(pattern);

        Object rawAlts = raw.get("safe_alternatives");
        if (rawAlts instanceof List<?> altList) {
            List<String> alts = altList.stream()
                    .filter(a -> a instanceof String)
                    .map(a -> (String) a)
                    .collect(Collectors.toUnmodifiableList());
            b.safeAlternatives(alts);
        }

        return b.build();
    }

    private static String requireString(Map<String, Object> raw, String key) {
        Object val = raw.get(key);
        if (!(val instanceof String s) || s.isBlank())
            throw new IllegalArgumentException("Required field '" + key + "' must be a non-blank string");
        return s;
    }

    private static String optString(Map<String, Object> raw, String key, String defaultValue) {
        Object val = raw.get(key);
        return (val instanceof String s) ? s : defaultValue;
    }

    private static int optInt(Map<String, Object> raw, String key, int defaultValue) {
        Object val = raw.get(key);
        if (val instanceof Integer i) return i;
        if (val instanceof String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
