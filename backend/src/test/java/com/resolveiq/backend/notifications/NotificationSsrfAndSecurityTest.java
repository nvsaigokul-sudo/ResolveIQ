package com.resolveiq.backend.notifications;

import com.resolveiq.backend.notifications.adapters.WebhookNotificationAdapter;
import com.resolveiq.backend.notifications.security.SsrfValidator;
import com.resolveiq.common.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSsrfAndSecurityTest {

    private SsrfValidator ssrfValidator;
    private WebhookNotificationAdapter webhookAdapter;

    @BeforeEach
    void setUp() {
        ssrfValidator = new SsrfValidator();
        webhookAdapter = new WebhookNotificationAdapter(ssrfValidator);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/webhook",
            "https://127.0.0.1:8443/alert",
            "http://localhost:8080/hook",
            "https://subdomain.localhost/hook",
            "http://10.0.0.1/internal",
            "http://10.254.1.10/webhook",
            "http://172.16.0.1/admin",
            "http://172.24.10.5/api",
            "http://172.31.255.255/hook",
            "http://192.168.0.1/router",
            "http://192.168.1.100/webhook",
            "http://169.254.169.254/latest/meta-data/",
            "http://169.254.1.1/hook",
            "http://0.0.0.0/test"
    })
    @DisplayName("SSRF Validator blocks loopback, private RFC 1918, link-local, and AWS metadata URLs")
    void testSsrfBlocksPrivateAndMetadataIps(String forbiddenUrl) {
        assertThatThrownBy(() -> ssrfValidator.validateUrl(forbiddenUrl))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("SSRF Protection");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://files.example.com/dump",
            "file:///etc/passwd",
            "gopher://example.com",
            "ldap://10.0.0.1:389"
    })
    @DisplayName("SSRF Validator rejects non-HTTP/HTTPS schemes")
    void testSsrfRejectsInvalidSchemes(String invalidSchemeUrl) {
        assertThatThrownBy(() -> ssrfValidator.validateUrl(invalidSchemeUrl))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Only HTTP and HTTPS are permitted");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://hooks.slack.example.com/services/test-incoming-webhook",
            "https://example.com/alerts/webhook",
            "https://api.opsgenie.com/v2/alerts"
    })
    @DisplayName("SSRF Validator permits legitimate external webhook destinations")
    void testSsrfAllowsLegitimateExternalUrls(String allowedUrl) {
        // Must complete without throwing ValidationException
        ssrfValidator.validateUrl(allowedUrl);
    }

    @Test
    @DisplayName("Webhook HMAC-SHA256 produces valid deterministic signature and detects tampering")
    void testHmacSha256SignatureAndTampering() {
        String secret = "production-grade-hmac-secret-key-98765";
        String payload = "{\"incident_id\":\"inc-1234\",\"severity\":\"SEV1\",\"title\":\"Payment Outage\"}";
        long timestamp = Instant.now().getEpochSecond();

        String signature = webhookAdapter.calculateHmacSha256(payload, secret, timestamp);
        assertThat(signature).isNotNull();
        assertThat(signature).startsWith("sha256=");

        // Deterministic generation check
        String identicalSignature = webhookAdapter.calculateHmacSha256(payload, secret, timestamp);
        assertThat(identicalSignature).isEqualTo(signature);

        // Tampering with payload changes signature
        String tamperedPayload = "{\"incident_id\":\"inc-1234\",\"severity\":\"SEV4\",\"title\":\"Payment Outage\"}";
        String tamperedSignature = webhookAdapter.calculateHmacSha256(tamperedPayload, secret, timestamp);
        assertThat(tamperedSignature).isNotEqualTo(signature);

        // Tampering with timestamp changes signature
        String timestampTamperedSignature = webhookAdapter.calculateHmacSha256(payload, secret, timestamp + 10);
        assertThat(timestampTamperedSignature).isNotEqualTo(signature);

        // Tampering with secret changes signature
        String wrongSecretSignature = webhookAdapter.calculateHmacSha256(payload, "wrong-secret", timestamp);
        assertThat(wrongSecretSignature).isNotEqualTo(signature);
    }
}
