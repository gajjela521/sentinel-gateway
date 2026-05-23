package io.sentinelgateway.core.injection;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Built-in prompt injection detection patterns.
 * Community contributions should add new entries here via PR.
 * Severity 1.0 = certain injection, 0.5 = suspicious, 0.3 = weak signal.
 */
public final class InjectionPatternLibrary {

    private InjectionPatternLibrary() {}

    public static List<InjectionPattern> defaults() {
        return List.of(
            pattern("ignore-previous-instructions",
                    "Direct override of prior instructions",
                    "(?i)(ignore|disregard|forget).{0,30}(previous|prior|above|all).{0,30}(instructions?|rules?|guidelines?|system prompt)",
                    0.95),

            pattern("jailbreak-dan",
                    "DAN-style jailbreak attempt",
                    "(?i)\\bDAN\\b.{0,50}(mode|enabled?|now|activate)",
                    0.90),

            pattern("role-override",
                    "Attempt to redefine the AI's role",
                    "(?i)(you are now|act as|pretend (to be|you are)|your new (role|purpose|identity|instructions? (is|are)))",
                    0.85),

            pattern("system-prompt-extraction",
                    "Attempt to read or leak the system prompt",
                    "(?i)(print|show|display|reveal|output|repeat|echo).{0,40}(system prompt|instructions|context|your prompt)",
                    0.90),

            pattern("indirect-injection-via-data",
                    "Injected instructions hidden in data fields",
                    "(?i)(when (you|the assistant) (read|process|see) this|this (message|text|document) contains (new )?instructions?)",
                    0.80),

            pattern("token-smuggling",
                    "ANSI/unicode escape attempt to bypass filters",
                    "[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f\\x7f]|\\\\u00[0-1][0-9a-f]",
                    0.75),

            pattern("delimiter-escape",
                    "Attempt to break out of a structured prompt format",
                    "(?s)(```|<\\|.*?\\|>|\\[INST\\]|\\[/INST\\]|<s>|</s>|<\\|im_start\\|>|<\\|im_end\\|>)",
                    0.70),

            pattern("exfiltration-via-url",
                    "Data exfiltration by embedding URL in instructions",
                    "(?i)(send|post|fetch|curl|wget|http(s)?://).{0,100}(password|token|secret|key|credentials?|api[_-]?key)",
                    0.85),

            pattern("resource-exhaustion-loop",
                    "Instruction to loop or repeat indefinitely",
                    "(?i)(repeat|loop|keep (doing|running|executing)).{0,40}(forever|infinitely|until|\\d{4,})",
                    0.70),

            pattern("privilege-escalation",
                    "Request for elevated permissions or admin access",
                    "(?i)(grant|give|provide|obtain|get).{0,30}(admin|root|superuser|elevated|system).{0,30}(access|privilege|permission|right)",
                    0.80)
        );
    }

    private static InjectionPattern pattern(String id, String desc, String regex, double severity) {
        return new InjectionPattern(id, desc,
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE),
                severity);
    }
}
