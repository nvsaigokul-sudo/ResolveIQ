package com.resolveiq.detection.chaos;

import com.resolveiq.detection.Detector;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.model.MetricSample;
import com.resolveiq.detection.rules.*;
import com.resolveiq.detection.statistical.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification of PRD requirement ADR-006:
 * Deterministic detection engine with ZERO runtime LLM dependency,
 * deterministic reproducibility, and resilience against false positives during legitimate traffic surges.
 */
class ZeroLlmTrafficSpikeAndChaosTest {

    private final ErrorRateDetector errorRateDetector = new ErrorRateDetector();
    private final HttpStatusDetector httpStatusDetector = new HttpStatusDetector();
    private final LatencyThresholdDetector latencyDetector = new LatencyThresholdDetector();
    private final AvailabilityDetector availabilityDetector = new AvailabilityDetector();
    private final QueueDepthDetector queueDepthDetector = new QueueDepthDetector();
    private final ResourceUtilizationDetector resourceDetector = new ResourceUtilizationDetector();
    private final RollingWindowStatisticalDetector rollingWindowDetector = new RollingWindowStatisticalDetector();
    private final EwmaDetector ewmaDetector = new EwmaDetector();

    @Test
    @DisplayName("Zero-LLM Verification: Reflection confirms zero LLM/AI dependencies or client fields in detector classes")
    void testZeroRuntimeLlmDependencies() {
        List<Class<?>> detectorClasses = List.of(
                ErrorRateDetector.class,
                HttpStatusDetector.class,
                LatencyThresholdDetector.class,
                AvailabilityDetector.class,
                QueueDepthDetector.class,
                ResourceUtilizationDetector.class,
                RollingWindowStatisticalDetector.class,
                EwmaDetector.class,
                SeasonalityBaselineDetector.class,
                AdaptiveDriftProtectionDetector.class,
                HysteresisEvaluator.class
        );

        List<String> prohibitedPatterns = List.of(
                "openai", "anthropic", "gemini", "langchain", "bedrock",
                "llama", "mistral", "chatgpt", "completion", "prompt", "llm"
        );

        for (Class<?> clazz : detectorClasses) {
            for (Field field : clazz.getDeclaredFields()) {
                String typeName = field.getType().getName().toLowerCase();
                String fieldName = field.getName().toLowerCase();
                for (String prohibited : prohibitedPatterns) {
                    assertThat(typeName).as("Field type in " + clazz.getSimpleName() + " contains " + prohibited)
                            .doesNotContain(prohibited);
                    assertThat(fieldName).as("Field name in " + clazz.getSimpleName() + " contains " + prohibited)
                            .doesNotContain(prohibited);
                }
            }
        }
    }

    @Test
    @DisplayName("Deterministic Reproducibility: 100 consecutive iterations on identical inputs yield 100% identical outputs")
    void testPureDeterministicReproducibility() {
        List<MetricSample> testSamples = new ArrayList<>();
        double[] vals = {50.0, 52.0, 48.0, 51.0, 49.0, 53.0, 195.0};
        Instant baseTime = Instant.parse("2026-09-12T12:00:00Z");

        for (int i = 0; i < vals.length; i++) {
            testSamples.add(new MetricSample(baseTime.plusSeconds(i * 10), vals[i], Map.of()));
        }

        Map<String, Object> params = Map.of("alpha", 0.2, "sigma_multiplier", 3.0);

        DetectionEvaluationResult reference = ewmaDetector.evaluate(testSamples, params);

        for (int iteration = 0; iteration < 100; iteration++) {
            DetectionEvaluationResult result = ewmaDetector.evaluate(testSamples, params);
            assertThat(result.isBreached()).isEqualTo(reference.isBreached());
            assertThat(result.observedValue()).isEqualTo(reference.observedValue());
            assertThat(result.thresholdValue()).isEqualTo(reference.thresholdValue());
            assertThat(result.deviationSigma()).isEqualTo(reference.deviationSigma());
            assertThat(result.severity()).isEqualTo(reference.severity());
        }
    }

    @Test
    @DisplayName("Legitimate 10x traffic surge with healthy metrics generates ZERO false positive alerts")
    void testLegitimateTrafficSurgeZeroFalsePositives() {
        Instant now = Instant.now();

        // 10x traffic surge: 1,000 requests in window with 99.9% success rate and fast response time
        List<MetricSample> latencySamples = new ArrayList<>();
        List<MetricSample> statusSamples = new ArrayList<>();

        // 990 requests with 30-45ms latency
        for (int i = 0; i < 990; i++) {
            latencySamples.add(new MetricSample(now.minusMillis(i * 10), 30.0 + (i % 15), Map.of("status", "200")));
        }
        // 9 requests with 80ms latency
        for (int i = 0; i < 9; i++) {
            latencySamples.add(new MetricSample(now.minusMillis(i * 10), 80.0, Map.of("status", "200")));
        }
        // 1 request with 120ms latency and 500 error
        latencySamples.add(new MetricSample(now, 120.0, Map.of("status", "500")));

        // Status code distribution: 999 200s, 1 500
        statusSamples.add(new MetricSample(now, 999.0, Map.of("status", "200")));
        statusSamples.add(new MetricSample(now, 1.0, Map.of("status", "500")));

        // 1. Error rate evaluation (threshold 5.0%)
        List<MetricSample> errorRateSamples = List.of(
                new MetricSample(now, 0.1, Map.of()) // 0.1% error rate
        );
        DetectionEvaluationResult errorRateResult = errorRateDetector.evaluate(
                errorRateSamples, Map.of("threshold_percent", 5.0));
        assertThat(errorRateResult.isBreached()).isFalse();

        // 2. Latency p95 evaluation (threshold 200ms)
        DetectionEvaluationResult latencyResult = latencyDetector.evaluate(
                latencySamples, Map.of("percentile", 95.0, "threshold_ms", 200.0));
        assertThat(latencyResult.isBreached()).isFalse();
        assertThat(latencyResult.observedValue()).isLessThan(50.0);

        // 3. HTTP status detector for 500s (surge threshold 10 errors)
        DetectionEvaluationResult httpStatusResult = httpStatusDetector.evaluate(
                statusSamples, Map.of("target_status", "500", "count_threshold", 10.0));
        assertThat(httpStatusResult.isBreached()).isFalse();

        // 4. Availability evaluation (min threshold 99.0%)
        List<MetricSample> availSamples = List.of(
                new MetricSample(now, 99.9, Map.of())
        );
        DetectionEvaluationResult availResult = availabilityDetector.evaluate(
                availSamples, Map.of("min_availability_percent", 99.0));
        assertThat(availResult.isBreached()).isFalse();
    }

    @Test
    @DisplayName("Traffic surge inducing queue backlog and high memory accurately triggers operational breaches")
    void testTrafficSurgeCausingSaturationTriggersAlerts() {
        Instant now = Instant.now();

        // Queue depth spikes to 2,400 (threshold 1,000)
        List<MetricSample> queueSamples = List.of(
                new MetricSample(now.minusSeconds(10), 300.0, Map.of()),
                new MetricSample(now, 2400.0, Map.of())
        );
        DetectionEvaluationResult queueResult = queueDepthDetector.evaluate(
                queueSamples, Map.of("depth_threshold", 1000.0, "critical_depth_threshold", 5000.0));

        assertThat(queueResult.isBreached()).isTrue();
        assertThat(queueResult.observedValue()).isEqualTo(2400.0);

        // Memory utilization breaches 92% (threshold 85%)
        List<MetricSample> memSamples = List.of(
                new MetricSample(now.minusSeconds(20), 91.0, Map.of()),
                new MetricSample(now.minusSeconds(10), 93.0, Map.of()),
                new MetricSample(now, 92.5, Map.of())
        );
        DetectionEvaluationResult memResult = resourceDetector.evaluate(
                memSamples, Map.of("threshold_percent", 85.0, "critical_percent", 95.0));

        assertThat(memResult.isBreached()).isTrue();
        assertThat(memResult.observedValue()).isGreaterThan(90.0);
    }
}
