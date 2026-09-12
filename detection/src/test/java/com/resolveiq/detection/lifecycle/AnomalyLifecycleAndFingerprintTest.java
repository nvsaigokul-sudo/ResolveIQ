package com.resolveiq.detection.lifecycle;

import com.resolveiq.common.telemetry.AnomalyLifecycleState;
import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.fingerprint.AnomalyFingerprinter;
import com.resolveiq.detection.model.AnomalyRecord;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.statistical.HysteresisEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyLifecycleAndFingerprintTest {

    private AnomalyFingerprinter fingerprinter;
    private AnomalyLifecycleManager lifecycleManager;

    @BeforeEach
    void setUp() {
        fingerprinter = new AnomalyFingerprinter();
        HysteresisEvaluator hysteresisEvaluator = new HysteresisEvaluator();
        // 15-minute cooldown per PRD §16
        lifecycleManager = new AnomalyLifecycleManager(fingerprinter, hysteresisEvaluator, 15);
    }

    @Test
    @DisplayName("Deterministic fingerprinting: Identical inputs produce exact SHA-256 hash; changes alter hash")
    void testDeterministicFingerprint() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String fp1 = fingerprinter.computeFingerprint("rule.error_rate_5xx", tenantId, "payment-service", "http_requests", "production");
        String fp2 = fingerprinter.computeFingerprint("rule.error_rate_5xx", tenantId, "payment-service", "http_requests", "production");

        assertThat(fp1).isNotNull().hasSize(64);
        assertThat(fp1).isEqualTo(fp2);

        // Different detector ID
        String fpDiffDetector = fingerprinter.computeFingerprint("rule.latency_threshold", tenantId, "payment-service", "http_requests", "production");
        assertThat(fp1).isNotEqualTo(fpDiffDetector);

        // Different tenant
        UUID tenant2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String fpDiffTenant = fingerprinter.computeFingerprint("rule.error_rate_5xx", tenant2, "payment-service", "http_requests", "production");
        assertThat(fp1).isNotEqualTo(fpDiffTenant);

        // Different environment
        String fpDiffEnv = fingerprinter.computeFingerprint("rule.error_rate_5xx", tenantId, "payment-service", "http_requests", "staging");
        assertThat(fp1).isNotEqualTo(fpDiffEnv);
    }

    @Test
    @DisplayName("Lifecycle transitions: OBSERVED -> EVALUATED -> RAISED, then DEDUPLICATED within cooldown")
    void testLifecycleTransitionsAndDeduplication() {
        UUID tenantId = UUID.randomUUID();
        String serviceId = "order-service";
        String metricName = "error_rate";

        DetectionEvaluationResult breach = DetectionEvaluationResult.breach(
                "rule.error_rate_5xx", DetectorType.RULE_BASED, 12.0, 5.0,
                Severity.CRITICAL, 5, null, "5xx error rate breach", Map.of()
        );

        // 1. Initial breach -> RAISED
        Optional<AnomalyRecord> raised = lifecycleManager.processEvaluation(
                tenantId, null, serviceId, metricName, "production", breach);

        assertThat(raised).isPresent();
        AnomalyRecord record = raised.get();
        assertThat(record.getLifecycleState()).isEqualTo(AnomalyLifecycleState.RAISED);
        assertThat(record.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(lifecycleManager.getActiveAnomalies()).hasSize(1);

        List<String> history = lifecycleManager.getTransitionHistory(record.getFingerprint());
        assertThat(history).anyMatch(h -> h.contains("OBSERVED"));
        assertThat(history).anyMatch(h -> h.contains("EVALUATED"));
        assertThat(history).anyMatch(h -> h.contains("RAISED"));

        // 2. Second breach within cooldown -> DEDUPLICATED (returns empty to prevent re-alerting)
        Optional<AnomalyRecord> secondBreach = lifecycleManager.processEvaluation(
                tenantId, null, serviceId, metricName, "production", breach);

        assertThat(secondBreach).isEmpty();
        assertThat(record.getLifecycleState()).isEqualTo(AnomalyLifecycleState.DEDUPLICATED);
        assertThat(record.getConsecutiveBreachCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Lifecycle recovery: Value drops below recovery threshold -> RESOLVED")
    void testLifecycleResolution() {
        UUID tenantId = UUID.randomUUID();
        String serviceId = "auth-service";
        String metricName = "latency_p95";

        DetectionEvaluationResult breach = DetectionEvaluationResult.breach(
                "rule.latency_threshold", DetectorType.RULE_BASED, 650.0, 500.0,
                Severity.HIGH, 10, null, "Latency breach", Map.of()
        );

        // Raise anomaly (threshold = 500.0, recovery = 425.0)
        Optional<AnomalyRecord> raised = lifecycleManager.processEvaluation(
                tenantId, null, serviceId, metricName, "production", breach);
        assertThat(raised).isPresent();

        // Normal cycle 1: value = 300.0 (< 425.0), but needs 2 cycles
        DetectionEvaluationResult normal1 = DetectionEvaluationResult.ok(
                "rule.latency_threshold", DetectorType.RULE_BASED, 300.0, 500.0, 10);
        Optional<AnomalyRecord> step1 = lifecycleManager.processEvaluation(
                tenantId, null, serviceId, metricName, "production", normal1);
        assertThat(step1).isEmpty(); // Not yet resolved

        // Normal cycle 2: value = 280.0 -> RESOLVED
        DetectionEvaluationResult normal2 = DetectionEvaluationResult.ok(
                "rule.latency_threshold", DetectorType.RULE_BASED, 280.0, 500.0, 10);
        Optional<AnomalyRecord> resolved = lifecycleManager.processEvaluation(
                tenantId, null, serviceId, metricName, "production", normal2);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getLifecycleState()).isEqualTo(AnomalyLifecycleState.RESOLVED);
        assertThat(lifecycleManager.getActiveAnomalies()).isEmpty();
    }

    @Test
    @DisplayName("Maintenance window: Breaches during active maintenance are SUPPRESSED")
    void testMaintenanceWindowSuppression() {
        UUID tenantId = UUID.randomUUID();
        String serviceId = "database-service";
        String metricName = "cpu_utilization";

        // Set 2-hour maintenance window
        lifecycleManager.setMaintenanceWindow(tenantId, serviceId, Instant.now().plusSeconds(7200));
        assertThat(lifecycleManager.isUnderMaintenance(tenantId, serviceId)).isTrue();

        DetectionEvaluationResult breach = DetectionEvaluationResult.breach(
                "rule.resource_utilization", DetectorType.RULE_BASED, 95.0, 85.0,
                Severity.HIGH, 5, null, "CPU breach during maintenance", Map.of()
        );

        Optional<AnomalyRecord> result = lifecycleManager.processEvaluation(
                tenantId, null, serviceId, metricName, "production", breach);

        // Suppressed -> no event emitted
        assertThat(result).isEmpty();
        assertThat(lifecycleManager.getActiveAnomalies()).isEmpty();
    }
}
