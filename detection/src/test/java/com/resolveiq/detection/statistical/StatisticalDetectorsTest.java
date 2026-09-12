package com.resolveiq.detection.statistical;

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

class StatisticalDetectorsTest {

    private final RollingWindowStatisticalDetector rollingWindowDetector = new RollingWindowStatisticalDetector();
    private final EwmaDetector ewmaDetector = new EwmaDetector();
    private final SeasonalityBaselineDetector seasonalityDetector = new SeasonalityBaselineDetector();
    private final AdaptiveDriftProtectionDetector driftDetector = new AdaptiveDriftProtectionDetector();
    private final HysteresisEvaluator hysteresisEvaluator = new HysteresisEvaluator();

    // ========================================================================
    // 1. Rolling-Window N-Sigma Statistical Detector
    // ========================================================================

    @Test
    @DisplayName("RollingWindow: Normal fluctuation within 3-sigma band produces no breach")
    void testRollingWindowNormal() {
        List<MetricSample> samples = new ArrayList<>();
        // 10 samples with mean 100 and slight variance
        double[] vals = {98, 102, 99, 101, 100, 97, 103, 100, 101, 102};
        for (double v : vals) {
            samples.add(new MetricSample(Instant.now(), v, Map.of()));
        }

        DetectionEvaluationResult result = rollingWindowDetector.evaluate(samples, Map.of("sigma_multiplier", 3.0));
        assertThat(result.isBreached()).isFalse();
    }

    @Test
    @DisplayName("RollingWindow: Statistical spike exceeding 3-sigma band triggers breach")
    void testRollingWindowSigmaBreach() {
        List<MetricSample> samples = new ArrayList<>();
        double[] vals = {98, 102, 99, 101, 100, 97, 103, 100, 101, 130.0}; // Spike to 130
        for (double v : vals) {
            samples.add(new MetricSample(Instant.now(), v, Map.of()));
        }

        DetectionEvaluationResult result = rollingWindowDetector.evaluate(samples, Map.of("sigma_multiplier", 3.0));
        assertThat(result.isBreached()).isTrue();
        assertThat(result.deviationSigma()).isGreaterThanOrEqualTo(3.0);
        assertThat(result.severity()).isEqualTo(Severity.CRITICAL);
    }

    // ========================================================================
    // 2. EWMA (Exponentially Weighted Moving Average)
    // ========================================================================

    @Test
    @DisplayName("EWMA: Sharp step change breaches dynamic Upper Control Limit")
    void testEwmaBreachOnStepChange() {
        List<MetricSample> samples = new ArrayList<>();
        // 8 stable readings around 50ms, then sudden jump to 250ms
        for (int i = 0; i < 8; i++) {
            samples.add(new MetricSample(Instant.now().minusSeconds(8 - i), 50.0 + (i % 2), Map.of()));
        }
        samples.add(new MetricSample(Instant.now(), 250.0, Map.of()));

        DetectionEvaluationResult result = ewmaDetector.evaluate(samples, Map.of(
                "alpha", 0.2,
                "sigma_multiplier", 3.0
        ));

        assertThat(result.isBreached()).isTrue();
        assertThat(result.observedValue()).isEqualTo(250.0);
        assertThat(result.deviationSigma()).isGreaterThanOrEqualTo(3.0);
    }

    // ========================================================================
    // 3. Seasonality-Aware Baseline (DoD / WoW)
    // ========================================================================

    @Test
    @DisplayName("SeasonalityBaseline: Detects abnormal volume divergence from historical baseline")
    void testSeasonalityBaselineBreach() {
        List<MetricSample> samples = List.of(
                new MetricSample(Instant.now().minusSeconds(10), 1800.0, Map.of()),
                new MetricSample(Instant.now(), 2000.0, Map.of())
        );

        // Expected baseline from yesterday/last week is 1000.0 (max allowed deviation 50%)
        DetectionEvaluationResult result = seasonalityDetector.evaluate(samples, Map.of(
                "seasonal_baseline", 1000.0,
                "max_deviation_percent", 50.0
        ));

        assertThat(result.isBreached()).isTrue();
        assertThat(result.observedValue()).isEqualTo(1900.0);
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
    }

    // ========================================================================
    // 4. Adaptive Drift Protection (Slow-onset creeping failure)
    // ========================================================================

    @Test
    @DisplayName("AdaptiveDriftProtection: Detects slow creeping trend before crash")
    void testSlowOnsetDriftDetected() {
        List<MetricSample> samples = new ArrayList<>();
        // Gradual increase from 100 to 150 over 9 samples
        double[] driftValues = {100, 105, 110, 118, 125, 132, 139, 145, 152};
        for (int i = 0; i < driftValues.length; i++) {
            samples.add(new MetricSample(Instant.now().minusSeconds(9 - i), driftValues[i], Map.of()));
        }

        DetectionEvaluationResult result = driftDetector.evaluate(samples, Map.of(
                "max_drift_percent", 30.0
        ));

        assertThat(result.isBreached()).isTrue();
        assertThat(result.message()).contains("Slow-onset adaptive drift detected");
    }

    @Test
    @DisplayName("AdaptiveDriftProtection: Stationary noisy metric does not trigger false drift")
    void testStationaryNoiseNoDrift() {
        List<MetricSample> samples = new ArrayList<>();
        double[] stableValues = {100, 104, 98, 102, 99, 103, 100, 97, 101};
        for (int i = 0; i < stableValues.length; i++) {
            samples.add(new MetricSample(Instant.now().minusSeconds(9 - i), stableValues[i], Map.of()));
        }

        DetectionEvaluationResult result = driftDetector.evaluate(samples, Map.of(
                "max_drift_percent", 30.0
        ));

        assertThat(result.isBreached()).isFalse();
    }

    // ========================================================================
    // 5. Dual-Threshold Hysteresis Evaluator
    // ========================================================================

    @Test
    @DisplayName("Hysteresis: Prevents flapping; requires consecutive normal cycles to resolve")
    void testHysteresisEvaluation() {
        double trigger = 500.0;
        double recovery = 350.0;

        // 1. Inactive -> value 520 crosses trigger -> becomes active
        boolean active = hysteresisEvaluator.evaluateActiveState(false, 520.0, trigger, recovery, 0, 2);
        assertThat(active).isTrue();

        // 2. Active -> value drops to 420 (in dead-band between 350 and 500) -> stays active
        active = hysteresisEvaluator.evaluateActiveState(true, 420.0, trigger, recovery, 0, 2);
        assertThat(active).isTrue();

        // 3. Active -> value drops to 320 (< 350 recovery), but count = 1 (< 2 required) -> stays active
        active = hysteresisEvaluator.evaluateActiveState(true, 320.0, trigger, recovery, 1, 2);
        assertThat(active).isTrue();

        // 4. Active -> value is 310 (< 350 recovery), count = 2 (meets 2 required) -> resolves
        active = hysteresisEvaluator.evaluateActiveState(true, 310.0, trigger, recovery, 2, 2);
        assertThat(active).isFalse();
    }
}
