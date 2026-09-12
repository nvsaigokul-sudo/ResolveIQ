package com.resolveiq.detection.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.telemetry.AnomalyLifecycleState;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.Detector;
import com.resolveiq.detection.fingerprint.AnomalyFingerprinter;
import com.resolveiq.detection.kafka.AnomalyEventProducer;
import com.resolveiq.detection.lifecycle.AnomalyLifecycleManager;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.rules.ErrorRateDetector;
import com.resolveiq.detection.rules.LatencyThresholdDetector;
import com.resolveiq.detection.statistical.EwmaDetector;
import com.resolveiq.detection.statistical.HysteresisEvaluator;
import com.resolveiq.detection.statistical.RollingWindowStatisticalDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-Tenant Isolation and Telemetry Robustness Tests (PRD §16, §17, §18).
 * Verifies 0% cross-tenant data/baseline leakage, independent sliding windows,
 * and robust handling of missing, delayed, and insufficient telemetry.
 */
class DetectionEngineCrossTenantIsolationTest {

    private DetectionEngineService detectionEngineService;
    private AnomalyLifecycleManager lifecycleManager;
    private AnomalyEventProducer eventProducer;
    private AnomalyFingerprinter fingerprinter;

    @BeforeEach
    void setUp() {
        fingerprinter = new AnomalyFingerprinter();
        HysteresisEvaluator hysteresisEvaluator = new HysteresisEvaluator();
        lifecycleManager = new AnomalyLifecycleManager(fingerprinter, hysteresisEvaluator, 15);

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        eventProducer = new AnomalyEventProducer(kafkaTemplate, new ObjectMapper(), "telemetry.anomalies");

        List<Detector> detectors = List.of(
                new ErrorRateDetector(),
                new LatencyThresholdDetector(),
                new RollingWindowStatisticalDetector(),
                new EwmaDetector()
        );

        detectionEngineService = new DetectionEngineService(detectors, lifecycleManager, eventProducer);
    }

    @Test
    @DisplayName("Cross-tenant isolation: Tenant Alpha error spike triggers anomaly; Tenant Beta remains 100% normal")
    void testCrossTenantIsolationOnErrorSpike() {
        UUID tenantAlpha = UUID.randomUUID();
        UUID tenantBeta = UUID.randomUUID();
        String sameServiceId = "order-service"; // Identical service name across tenants

        Instant now = Instant.now();

        // 1. Ingest normal telemetry for Tenant Beta (0.2% error rate)
        for (int i = 0; i < 5; i++) {
            DetectionEvaluationResult resultBeta = detectionEngineService.ingestAndEvaluate(
                    tenantBeta, null, sameServiceId, "error_rate", "production",
                    0.2, Map.of(), now.minusSeconds(10 - i), "rule.error_rate_5xx",
                    Map.of("threshold_percent", 5.0)
            );
            assertThat(resultBeta.isBreached()).isFalse();
        }

        // 2. Ingest critical error spike for Tenant Alpha (15% error rate)
        DetectionEvaluationResult resultAlpha = null;
        for (int i = 0; i < 5; i++) {
            resultAlpha = detectionEngineService.ingestAndEvaluate(
                    tenantAlpha, null, sameServiceId, "error_rate", "production",
                    15.0, Map.of(), now.minusSeconds(10 - i), "rule.error_rate_5xx",
                    Map.of("threshold_percent", 5.0, "critical_threshold_percent", 10.0)
            );
        }

        // Assertions for Tenant Alpha
        assertThat(resultAlpha).isNotNull();
        assertThat(resultAlpha.isBreached()).isTrue();
        assertThat(resultAlpha.severity()).isEqualTo(Severity.CRITICAL);

        // Verify active anomalies in lifecycle manager: exactly 1, belonging strictly to Tenant Alpha
        assertThat(lifecycleManager.getActiveAnomalies()).hasSize(1);
        var activeAnomaly = lifecycleManager.getActiveAnomalies().values().iterator().next();
        assertThat(activeAnomaly.getTenantId()).isEqualTo(tenantAlpha);
        assertThat(activeAnomaly.getServiceId()).isEqualTo(sameServiceId);
        assertThat(activeAnomaly.getLifecycleState()).isEqualTo(AnomalyLifecycleState.DEDUPLICATED);
        // First 2 samples satisfied min_samples threshold (3), subsequent 3 samples recorded consecutive breaches
        assertThat(activeAnomaly.getConsecutiveBreachCount()).isEqualTo(3);

        // Verify Kafka event published strictly for Tenant Alpha (1 initial RAISED event)
        assertThat(eventProducer.getEmittedEvents()).hasSize(1);
        var emitted = eventProducer.getEmittedEvents().peek();
        assertThat(emitted.tenantId()).isEqualTo(tenantAlpha);
        assertThat(emitted.payload().serviceId()).isEqualTo(sameServiceId);
        assertThat(emitted.payload().severity()).isEqualTo(Severity.CRITICAL);

        // 3. Re-verify Tenant Beta remains 100% unaffected by Alpha's incident
        DetectionEvaluationResult betaPostAlphaSpike = detectionEngineService.ingestAndEvaluate(
                tenantBeta, null, sameServiceId, "error_rate", "production",
                0.3, Map.of(), now, "rule.error_rate_5xx",
                Map.of("threshold_percent", 5.0)
        );
        assertThat(betaPostAlphaSpike.isBreached()).isFalse();
        assertThat(lifecycleManager.getActiveAnomalies()).hasSize(1); // Still only Alpha's anomaly
    }

    @Test
    @DisplayName("Cross-tenant baseline isolation: High-latency tenant baseline does not contaminate low-latency tenant baseline")
    void testCrossTenantStatisticalBaselineIsolation() {
        UUID tenantHeavyBatch = UUID.randomUUID();
        UUID tenantLowLatency = UUID.randomUUID();
        String serviceId = "data-processor";
        Instant now = Instant.now();

        // Feed heavy tenant high baseline (mean ~2000ms)
        for (int i = 0; i < 15; i++) {
            detectionEngineService.ingestAndEvaluate(
                    tenantHeavyBatch, null, serviceId, "latency", "production",
                    2000.0 + (i % 3) * 10, Map.of(), now.minusSeconds(30 - i),
                    "stat.rolling_window_sigma", Map.of("sigma_multiplier", 3.0, "min_samples", 5)
            );
        }

        // Feed low latency tenant tight baseline (mean ~10ms)
        for (int i = 0; i < 15; i++) {
            detectionEngineService.ingestAndEvaluate(
                    tenantLowLatency, null, serviceId, "latency", "production",
                    10.0 + (i % 2), Map.of(), now.minusSeconds(30 - i),
                    "stat.rolling_window_sigma", Map.of("sigma_multiplier", 3.0, "min_samples", 5)
            );
        }

        // Now Low Latency tenant spikes to 80ms (huge 8x spike relative to 10ms, but tiny relative to 2000ms)
        DetectionEvaluationResult lowLatencySpikeResult = detectionEngineService.ingestAndEvaluate(
                tenantLowLatency, null, serviceId, "latency", "production",
                80.0, Map.of(), now,
                "stat.rolling_window_sigma", Map.of("sigma_multiplier", 3.0, "min_samples", 5)
        );

        // If baselines leaked, 80ms would be below 2000ms and missed.
        // With strict isolation, it breaches Low Latency tenant's baseline!
        assertThat(lowLatencySpikeResult.isBreached()).isTrue();
        assertThat(lowLatencySpikeResult.observedValue()).isEqualTo(80.0);
        assertThat(lowLatencySpikeResult.deviationSigma()).isGreaterThanOrEqualTo(3.0);

        // Verify Heavy Batch tenant receiving 2020ms remains normal
        DetectionEvaluationResult heavyBatchNormalResult = detectionEngineService.ingestAndEvaluate(
                tenantHeavyBatch, null, serviceId, "latency", "production",
                2020.0, Map.of(), now,
                "stat.rolling_window_sigma", Map.of("sigma_multiplier", 3.0, "min_samples", 5)
        );
        assertThat(heavyBatchNormalResult.isBreached()).isFalse();
    }

    @Test
    @DisplayName("Telemetry robustness: Insufficient samples returns safe result without false positives")
    void testInsufficientSamplesHandledSafely() {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        // Feed only 2 samples when detector requires minimum 10
        DetectionEvaluationResult res1 = detectionEngineService.ingestAndEvaluate(
                tenantId, null, "auth-service", "latency", "production",
                9999.0, Map.of(), now.minusSeconds(2),
                "stat.rolling_window_sigma", Map.of("sigma_multiplier", 3.0, "min_samples", 10)
        );
        DetectionEvaluationResult res2 = detectionEngineService.ingestAndEvaluate(
                tenantId, null, "auth-service", "latency", "production",
                9999.0, Map.of(), now,
                "stat.rolling_window_sigma", Map.of("sigma_multiplier", 3.0, "min_samples", 10)
        );

        assertThat(res1.isBreached()).isFalse();
        assertThat(res1.message()).contains("Insufficient telemetry samples");
        assertThat(res2.isBreached()).isFalse();
        assertThat(res2.message()).contains("Insufficient telemetry samples");
        assertThat(lifecycleManager.getActiveAnomalies()).isEmpty();
    }

    @Test
    @DisplayName("Telemetry robustness: Out-of-order and delayed samples accommodated within sliding window")
    void testOutOfOrderAndDelayedTelemetry() {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        // Sample at T=now
        detectionEngineService.ingestAndEvaluate(
                tenantId, null, "api-gateway", "error_rate", "production",
                1.0, Map.of(), now, "rule.error_rate_5xx", Map.of("threshold_percent", 5.0)
        );

        // Sample arriving delayed from T = now - 5 minutes (valid within 15-min window)
        detectionEngineService.ingestAndEvaluate(
                tenantId, null, "api-gateway", "error_rate", "production",
                1.2, Map.of(), now.minusSeconds(300), "rule.error_rate_5xx", Map.of("threshold_percent", 5.0)
        );

        // Sample arriving delayed from T = now - 20 minutes (exceeds 15-min window, pruned)
        detectionEngineService.ingestAndEvaluate(
                tenantId, null, "api-gateway", "error_rate", "production",
                20.0, Map.of(), now.minusSeconds(1200), "rule.error_rate_5xx", Map.of("threshold_percent", 5.0)
        );

        // Final evaluation at T = now
        DetectionEvaluationResult result = detectionEngineService.ingestAndEvaluate(
                tenantId, null, "api-gateway", "error_rate", "production",
                1.1, Map.of(), now, "rule.error_rate_5xx", Map.of("threshold_percent", 5.0)
        );

        // Because the ancient 20-min-old 20% spike was pruned by the 15-minute sliding window cutoff,
        // it does not trigger a false alarm now.
        assertThat(result.isBreached()).isFalse();
        assertThat(lifecycleManager.getActiveAnomalies()).isEmpty();
    }
}
