package com.resolveiq.detection.rules;

import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.model.MetricSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedDetectorsTest {

    private final ErrorRateDetector errorRateDetector = new ErrorRateDetector();
    private final HttpStatusDetector httpStatusDetector = new HttpStatusDetector();
    private final LatencyThresholdDetector latencyDetector = new LatencyThresholdDetector();
    private final ResourceUtilizationDetector resourceDetector = new ResourceUtilizationDetector();
    private final QueueDepthDetector queueDepthDetector = new QueueDepthDetector();
    private final AvailabilityDetector availabilityDetector = new AvailabilityDetector();

    // ========================================================================
    // 1. Error Rate Detector (5xx)
    // ========================================================================

    @Test
    @DisplayName("ErrorRateDetector: Normal error rate below 5% produces no breach")
    void testErrorRateNormal() {
        List<MetricSample> samples = List.of(
                new MetricSample(Instant.now().minusSeconds(20), 1.2, Map.of()),
                new MetricSample(Instant.now().minusSeconds(10), 0.8, Map.of()),
                new MetricSample(Instant.now(), 1.5, Map.of())
        );

        DetectionEvaluationResult result = errorRateDetector.evaluate(samples, Map.of("threshold_percent", 5.0));
        assertThat(result.isBreached()).isFalse();
        assertThat(result.observedValue()).isLessThan(5.0);
    }

    @Test
    @DisplayName("ErrorRateDetector: Error rate >5% triggers HIGH breach; >10% triggers CRITICAL")
    void testErrorRateBreach() {
        List<MetricSample> highSamples = List.of(
                new MetricSample(Instant.now().minusSeconds(20), 6.5, Map.of()),
                new MetricSample(Instant.now().minusSeconds(10), 7.0, Map.of()),
                new MetricSample(Instant.now(), 6.0, Map.of())
        );
        DetectionEvaluationResult highResult = errorRateDetector.evaluate(highSamples, Map.of("threshold_percent", 5.0, "critical_threshold_percent", 10.0));
        assertThat(highResult.isBreached()).isTrue();
        assertThat(highResult.severity()).isEqualTo(Severity.HIGH);

        List<MetricSample> critSamples = List.of(
                new MetricSample(Instant.now().minusSeconds(20), 14.5, Map.of()),
                new MetricSample(Instant.now().minusSeconds(10), 16.0, Map.of()),
                new MetricSample(Instant.now(), 12.0, Map.of())
        );
        DetectionEvaluationResult critResult = errorRateDetector.evaluate(critSamples, Map.of("threshold_percent", 5.0, "critical_threshold_percent", 10.0));
        assertThat(critResult.isBreached()).isTrue();
        assertThat(critResult.severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    @DisplayName("ErrorRateDetector: Insufficient samples returns insufficientData without false alarm")
    void testErrorRateInsufficientSamples() {
        List<MetricSample> samples = List.of(
                new MetricSample(Instant.now(), 100.0, Map.of())
        );
        DetectionEvaluationResult result = errorRateDetector.evaluate(samples, Map.of("min_samples", 3));
        assertThat(result.isBreached()).isFalse();
        assertThat(result.message()).contains("Insufficient telemetry samples");
    }

    // ========================================================================
    // 2. HTTP Status Code Detector
    // ========================================================================

    @Test
    @DisplayName("HttpStatusDetector: Flags 503 error count surge exceeding threshold")
    void testHttpStatusDetectorBreach() {
        List<MetricSample> samples = List.of(
                new MetricSample(Instant.now().minusSeconds(10), 15.0, Map.of("status", "503")),
                new MetricSample(Instant.now(), 12.0, Map.of("status", "503")),
                new MetricSample(Instant.now(), 200.0, Map.of("status", "200"))
        );

        DetectionEvaluationResult result = httpStatusDetector.evaluate(samples, Map.of(
                "target_status", "503",
                "count_threshold", 20.0
        ));

        assertThat(result.isBreached()).isTrue();
        assertThat(result.observedValue()).isEqualTo(27.0);
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    // ========================================================================
    // 3. Latency Percentile Detector (p95 / p99)
    // ========================================================================

    @Test
    @DisplayName("LatencyThresholdDetector: Correctly computes p95 and triggers breach")
    void testLatencyPercentileBreach() {
        // 20 requests: 18 fast (50-100ms), 2 slow (600ms, 850ms)
        List<MetricSample> samples = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            samples.add(new MetricSample(Instant.now().minusSeconds(i), 80.0 + i, Map.of()));
        }
        samples.add(new MetricSample(Instant.now().minusSeconds(1), 600.0, Map.of()));
        samples.add(new MetricSample(Instant.now(), 850.0, Map.of()));

        // p95 of 20 elements is 19th element (600.0ms)
        DetectionEvaluationResult result = latencyDetector.evaluate(samples, Map.of(
                "percentile", 95.0,
                "threshold_ms", 500.0,
                "critical_threshold_ms", 1000.0
        ));

        assertThat(result.isBreached()).isTrue();
        assertThat(result.observedValue()).isEqualTo(600.0);
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    // ========================================================================
    // 4. Resource Utilization Detector (CPU / Memory)
    // ========================================================================

    @Test
    @DisplayName("ResourceUtilizationDetector: Triggers breach when utilization exceeds 85%")
    void testResourceUtilizationBreach() {
        List<MetricSample> samples = List.of(
                new MetricSample(Instant.now().minusSeconds(20), 87.0, Map.of()),
                new MetricSample(Instant.now().minusSeconds(10), 91.0, Map.of()),
                new MetricSample(Instant.now(), 89.0, Map.of())
        );

        DetectionEvaluationResult result = resourceDetector.evaluate(samples, Map.of(
                "threshold_percent", 85.0,
                "critical_percent", 95.0
        ));

        assertThat(result.isBreached()).isTrue();
        assertThat(result.observedValue()).isEqualTo(89.0);
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    // ========================================================================
    // 5. Queue Depth Detector
    // ========================================================================

    @Test
    @DisplayName("QueueDepthDetector: Triggers breach on message backlog exceeding buffer")
    void testQueueDepthBreach() {
        List<MetricSample> samples = List.of(
                new MetricSample(Instant.now().minusSeconds(10), 400.0, Map.of()),
                new MetricSample(Instant.now(), 1500.0, Map.of())
        );

        DetectionEvaluationResult result = queueDepthDetector.evaluate(samples, Map.of(
                "depth_threshold", 1000.0,
                "critical_depth_threshold", 5000.0
        ));

        assertThat(result.isBreached()).isTrue();
        assertThat(result.observedValue()).isEqualTo(1500.0);
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    // ========================================================================
    // 6. Availability Detector
    // ========================================================================

    @Test
    @DisplayName("AvailabilityDetector: Triggers breach when availability drops below 99.0%")
    void testAvailabilityBreach() {
        List<MetricSample> samples = List.of(
                new MetricSample(Instant.now().minusSeconds(20), 98.5, Map.of()),
                new MetricSample(Instant.now().minusSeconds(10), 97.0, Map.of()),
                new MetricSample(Instant.now(), 98.0, Map.of())
        );

        DetectionEvaluationResult result = availabilityDetector.evaluate(samples, Map.of(
                "min_availability_percent", 99.0,
                "critical_availability_percent", 95.0
        ));

        assertThat(result.isBreached()).isTrue();
        assertThat(result.observedValue()).isLessThan(99.0);
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }
}
