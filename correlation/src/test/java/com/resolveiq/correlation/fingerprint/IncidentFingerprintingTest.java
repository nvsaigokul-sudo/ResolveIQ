package com.resolveiq.correlation.fingerprint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic Incident Fingerprinting Tests (PRD §19, §20).
 * Verifies SHA-256 computation, case-insensitivity, whitespace trimming,
 * and differentiation by tenant, service, detector, and error signature.
 */
class IncidentFingerprintingTest {

    private IncidentFingerprinter fingerprinter;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        fingerprinter = new IncidentFingerprinter();
        tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    @Test
    @DisplayName("Deterministic SHA-256: Identical inputs produce exact 64-char hex hash")
    void testExactFingerprintReproducibility() {
        String fp1 = fingerprinter.computeFingerprint(tenantId, "payment-service", "RULE_BASED", "status_500");
        String fp2 = fingerprinter.computeFingerprint(tenantId, "payment-service", "RULE_BASED", "status_500");

        assertThat(fp1).isNotNull().hasSize(64);
        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    @DisplayName("Canonical Normalization: Case and leading/trailing whitespace do not alter hash")
    void testNormalizationImmunity() {
        String fp1 = fingerprinter.computeFingerprint(tenantId, "Payment-Service ", "rule_based", "status_500");
        String fp2 = fingerprinter.computeFingerprint(tenantId, " payment-service", "RULE_BASED", " STATUS_500 ");

        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    @DisplayName("Field Differentiation: Changing any constituent component alters the fingerprint")
    void testFieldDifferentiation() {
        String baseline = fingerprinter.computeFingerprint(tenantId, "payment-service", "RULE_BASED", "status_500");

        // Different tenant
        UUID otherTenant = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        String diffTenant = fingerprinter.computeFingerprint(otherTenant, "payment-service", "RULE_BASED", "status_500");
        assertThat(baseline).isNotEqualTo(diffTenant);

        // Different service
        String diffService = fingerprinter.computeFingerprint(tenantId, "order-service", "RULE_BASED", "status_500");
        assertThat(baseline).isNotEqualTo(diffService);

        // Different detector type
        String diffType = fingerprinter.computeFingerprint(tenantId, "payment-service", "STATISTICAL", "status_500");
        assertThat(baseline).isNotEqualTo(diffType);

        // Different error signature
        String diffError = fingerprinter.computeFingerprint(tenantId, "payment-service", "RULE_BASED", "timeout_error");
        assertThat(baseline).isNotEqualTo(diffError);
    }
}
