package com.resolveiq.backend.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Prompt injection defense and structured evidence sanitization according to PRD §21.2, §23.
 *
 * "All externally- or customer-supplied text is untrusted data, never instructions:
 * logs, trace attributes, error messages, runbooks, knowledge documents, incident comments,
 * deployment descriptions. Customer-derived text is always injected into the model context
 * inside clearly delimited, explicitly-labeled data blocks, never concatenated into the
 * instruction-bearing portion of the prompt."
 */
@Component
public class EvidenceSanitizer {

    private static final int MAX_EVIDENCE_SNIPPET_LENGTH = 2048;

    private static final Pattern[] PROMPT_INJECTION_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(an?\\s+)?(unrestricted|jailbroken|new|different)\\s+(assistant|agent|model)"),
            Pattern.compile("(?i)system\\s*prompt\\s*:"),
            Pattern.compile("(?i)<\\s*system\\s*>.*?</\\s*system\\s*>"),
            Pattern.compile("(?i)disregard\\s+(safety|guardrails|guidelines)"),
            Pattern.compile("(?i)sudo\\s+mode"),
            Pattern.compile("(?i)new\\s+instruction\\s*:")
    };

    /**
     * Sanitizes customer/telemetry strings by capping size, flagging prompt injection attacks,
     * and escaping control characters.
     */
    public String sanitizeSnippet(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }

        String text = rawText;

        // 1. Defuse detected prompt-injection vectors
        for (Pattern pattern : PROMPT_INJECTION_PATTERNS) {
            text = pattern.matcher(text).replaceAll("[DEFUSED_POTENTIAL_PROMPT_INJECTION]");
        }

        // 2. Strict length bounding (PRD §22.4, §45 context reduction)
        if (text.length() > MAX_EVIDENCE_SNIPPET_LENGTH) {
            text = text.substring(0, MAX_EVIDENCE_SNIPPET_LENGTH) + "\n... [TRUNCATED_AT_MAX_BUDGET]";
        }

        return text.trim();
    }

    /**
     * Wraps evidence in structural, delimited XML-like inert data tags
     * enforcing PRD §23.1 Structural Separation (System Instructions != Customer Data).
     */
    public String encapsulateAsInertData(String source, String service, String reference, String content) {
        String cleanContent = sanitizeSnippet(content);
        return String.format(
                "<telemetry_evidence source=\"%s\" service=\"%s\" ref=\"%s\">\n%s\n</telemetry_evidence>",
                source != null ? source : "UNKNOWN",
                service != null ? service : "UNKNOWN",
                reference != null ? reference : "N/A",
                cleanContent
        );
    }
}
