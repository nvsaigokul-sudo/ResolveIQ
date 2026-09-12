package com.resolveiq.ingestion.redaction;

import com.resolveiq.common.telemetry.LogObservedPayload;
import com.resolveiq.common.telemetry.MetricObservedPayload;
import com.resolveiq.common.telemetry.TraceObservedPayload;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defense-in-depth PII and Secret Redaction Engine (PRD Section 14, 15, 34).
 * Strips emails, credit cards, API keys, passwords, and JWT tokens before telemetry enters Kafka.
 */
@Component
public class TelemetryRedactor {

    // 1. Email Pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b"
    );

    // 2. Credit Card Pattern (13 to 19 digits with optional hyphens or spaces)
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b"
    );

    // 3. API Key Patterns (ResolveIQ keys riq_live_..., generic sk-...)
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "\\b(riq_live_[a-zA-Z0-9_-]{16,}|sk-[a-zA-Z0-9]{20,})\\b"
    );

    // 4. JWT / Bearer Token Pattern
    private static final Pattern JWT_PATTERN = Pattern.compile(
            "Bearer\\s+([A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*)"
    );

    // 5. Common sensitive key names to mask values completely
    private static final Set<String> SENSITIVE_KEY_NAMES = Set.of(
            "password", "secret", "token", "apikey", "api_key", "authorization",
            "private_key", "access_token", "refresh_token", "cvv", "ssn"
    );

    public String redactText(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;

        // Mask JWT tokens first
        Matcher jwtMatcher = JWT_PATTERN.matcher(result);
        if (jwtMatcher.find()) {
            result = jwtMatcher.replaceAll("Bearer [REDACTED_JWT]");
        }

        // Mask ResolveIQ API keys
        Matcher keyMatcher = API_KEY_PATTERN.matcher(result);
        if (keyMatcher.find()) {
            result = keyMatcher.replaceAll("[REDACTED_API_KEY]");
        }

        // Mask Emails
        Matcher emailMatcher = EMAIL_PATTERN.matcher(result);
        if (emailMatcher.find()) {
            result = emailMatcher.replaceAll("[REDACTED_EMAIL]");
        }

        // Mask Credit Cards
        Matcher cardMatcher = CREDIT_CARD_PATTERN.matcher(result);
        if (cardMatcher.find()) {
            result = cardMatcher.replaceAll("[REDACTED_CREDIT_CARD]");
        }

        return result;
    }

    public Map<String, Object> redactMap(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key)) {
                redacted.put(key, "[REDACTED_SECRET]");
            } else if (value instanceof String strVal) {
                redacted.put(key, redactText(strVal));
            } else if (value instanceof Map<?, ?> mapVal) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castMap = (Map<String, Object>) mapVal;
                redacted.put(key, redactMap(castMap));
            } else if (value instanceof List<?> listVal) {
                redacted.put(key, redactList(listVal));
            } else {
                redacted.put(key, value);
            }
        }
        return redacted;
    }

    public Map<String, String> redactStringMap(Map<String, String> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (isSensitiveKey(key)) {
                redacted.put(key, "[REDACTED_SECRET]");
            } else {
                redacted.put(key, redactText(value));
            }
        }
        return redacted;
    }

    public List<?> redactList(List<?> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> redacted = new ArrayList<>();
        for (Object item : input) {
            if (item instanceof String strItem) {
                redacted.add(redactText(strItem));
            } else if (item instanceof Map<?, ?> mapItem) {
                @SuppressWarnings("unchecked")
                Map<String, Object> castMap = (Map<String, Object>) mapItem;
                redacted.add(redactMap(castMap));
            } else {
                redacted.add(item);
            }
        }
        return redacted;
    }

    public MetricObservedPayload redactMetric(MetricObservedPayload metric) {
        if (metric == null) return null;
        return new MetricObservedPayload(
                metric.metricName(),
                metric.metricType(),
                metric.value(),
                metric.buckets(),
                redactStringMap(metric.labels()),
                metric.serviceName(),
                redactStringMap(metric.resourceAttributes()),
                metric.timestamp()
        );
    }

    public LogObservedPayload redactLog(LogObservedPayload log) {
        if (log == null) return null;
        return new LogObservedPayload(
                log.timestamp(),
                log.severity(),
                redactText(log.body()),
                redactMap(log.attributes()),
                log.serviceName(),
                log.deploymentEnvironment(),
                log.traceId(),
                log.spanId(),
                redactStringMap(log.resourceAttributes())
        );
    }

    public TraceObservedPayload redactTrace(TraceObservedPayload trace) {
        if (trace == null) return null;
        return new TraceObservedPayload(
                trace.traceId(),
                trace.spanId(),
                trace.parentSpanId(),
                trace.serviceName(),
                trace.operationName(),
                trace.startTime(),
                trace.durationMs(),
                trace.status(),
                redactMap(trace.attributes()),
                redactStringMap(trace.resourceAttributes())
        );
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return SENSITIVE_KEY_NAMES.contains(lower) ||
                lower.contains("password") ||
                lower.contains("secret") ||
                lower.contains("token") ||
                lower.contains("api_key");
    }
}
