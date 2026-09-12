package com.resolveiq.ingestion.redaction;

import com.resolveiq.common.telemetry.LogObservedPayload;
import com.resolveiq.common.telemetry.MetricObservedPayload;
import com.resolveiq.common.telemetry.TraceObservedPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TelemetryRedactorTest {

    private TelemetryRedactor redactor;

    @BeforeEach
    void setUp() {
        redactor = new TelemetryRedactor();
    }

    @Test
    @DisplayName("Redacts emails, credit cards, API keys, and JWT tokens from raw text")
    void testTextRedaction() {
        String input = "User priya.sre@resolveiq.io reported failure with card 4111-2222-3333-4444 " +
                "using key riq_live_abc1234567890xyz_secret and Authorization Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.xyz";

        String redacted = redactor.redactText(input);

        assertThat(redacted).doesNotContain("priya.sre@resolveiq.io");
        assertThat(redacted).contains("[REDACTED_EMAIL]");

        assertThat(redacted).doesNotContain("4111-2222-3333-4444");
        assertThat(redacted).contains("[REDACTED_CREDIT_CARD]");

        assertThat(redacted).doesNotContain("riq_live_abc1234567890xyz_secret");
        assertThat(redacted).contains("[REDACTED_API_KEY]");

        assertThat(redacted).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        assertThat(redacted).contains("Bearer [REDACTED_JWT]");
    }

    @Test
    @DisplayName("Redacts sensitive keys in maps completely")
    void testMapRedaction() {
        Map<String, Object> attributes = Map.of(
                "db.password", "super-secret-db-pass",
                "http.user_email", "daniel.engineer@company.com",
                "api_key", "sk-12345678901234567890",
                "custom.message", "Clean log line"
        );

        Map<String, Object> redacted = redactor.redactMap(attributes);

        assertThat(redacted.get("db.password")).isEqualTo("[REDACTED_SECRET]");
        assertThat(redacted.get("api_key")).isEqualTo("[REDACTED_SECRET]");
        assertThat(redacted.get("http.user_email")).isEqualTo("[REDACTED_EMAIL]");
        assertThat(redacted.get("custom.message")).isEqualTo("Clean log line");
    }

    @Test
    @DisplayName("Redacts nested structures in LogObservedPayload")
    void testLogPayloadRedaction() {
        LogObservedPayload log = new LogObservedPayload(
                Instant.now(),
                "ERROR",
                "Failed login for admin@corp.net with key riq_live_9876543210abcdef",
                Map.of(
                        "auth.token", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-ID",
                        "nested", Map.of("email", "support@vendor.com")
                ),
                "auth-service",
                "production",
                "trace-1",
                "span-1",
                Map.of("host.name", "prod-node-1")
        );

        LogObservedPayload redacted = redactor.redactLog(log);

        assertThat(redacted.body()).doesNotContain("admin@corp.net");
        assertThat(redacted.body()).contains("[REDACTED_EMAIL]");
        assertThat(redacted.body()).doesNotContain("riq_live_9876543210abcdef");
        assertThat(redacted.body()).contains("[REDACTED_API_KEY]");

        assertThat(redacted.attributes().get("auth.token")).isEqualTo("[REDACTED_SECRET]");

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) redacted.attributes().get("nested");
        assertThat(nested.get("email")).isEqualTo("[REDACTED_EMAIL]");
    }

    @Test
    @DisplayName("Redacts sensitive data in MetricObservedPayload labels and resource attributes")
    void testMetricPayloadRedaction() {
        MetricObservedPayload metric = new MetricObservedPayload(
                "http.server.requests",
                MetricObservedPayload.MetricType.COUNTER,
                150.0,
                null,
                Map.of("client_email", "customer@shop.com"),
                "order-service",
                Map.of("token", "secret-token-123"),
                Instant.now()
        );

        MetricObservedPayload redacted = redactor.redactMetric(metric);

        assertThat(redacted.labels().get("client_email")).isEqualTo("[REDACTED_EMAIL]");
        assertThat(redacted.resourceAttributes().get("token")).isEqualTo("[REDACTED_SECRET]");
    }
}
