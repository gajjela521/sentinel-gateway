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
import java.util.Set;
import java.util.stream.Collectors;

/** Loads policy rules from YAML. See sentinel-default-policies.yaml for the schema. */
public final class PolicyLoader {

    private static final Logger log = LoggerFactory.getLogger(PolicyLoader.class);
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @SuppressWarnings("unchecked")
    public static List<PolicyRule> fromYaml(InputStream input) throws IOException {
        Map<String, Object> root = YAML.readValue(input, Map.class);
        List<Map<String, Object>> rawRules = (List<Map<String, Object>>) root.get("rules");
        if (rawRules == null) return List.of();

        List<PolicyRule> result = new ArrayList<>();
        for (Map<String, Object> raw : rawRules) {
            try {
                result.add(parseRule(raw));
            } catch (Exception e) {
                log.warn("Skipping malformed rule {}: {}", raw.get("id"), e.getMessage());
            }
        }
        log.info("Loaded {} policy rules from YAML", result.size());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static PolicyRule parseRule(Map<String, Object> raw) {
        PolicyRule.Builder b = PolicyRule.builder()
                .id((String) raw.get("id"))
                .description((String) raw.getOrDefault("description", ""))
                .action(PolicyRule.Action.valueOf(((String) raw.getOrDefault("action", "BLOCK")).toUpperCase()))
                .agentGuidance((String) raw.get("agent_guidance"))
                .priority((Integer) raw.getOrDefault("priority", 100));

        List<String> rawMethods = (List<String>) raw.get("methods");
        if (rawMethods != null) {
            Set<HttpMethod> methods = rawMethods.stream()
                    .map(m -> HttpMethod.valueOf(m.toUpperCase()))
                    .collect(Collectors.toSet());
            b.methods(methods);
        }

        String pattern = (String) raw.get("endpoint_pattern");
        if (pattern != null) b.endpointPattern(pattern);

        List<String> alts = (List<String>) raw.get("safe_alternatives");
        if (alts != null) b.safeAlternatives(alts);

        return b.build();
    }
}
