package io.sentinelgateway.core.injection;

import java.util.regex.Pattern;

/**
 * A single named injection detection pattern with a severity score [0.0, 1.0].
 */
public record InjectionPattern(
        String id,
        String description,
        Pattern regex,
        double severity
) {
    public boolean matches(String text) {
        return regex.matcher(text).find();
    }
}
