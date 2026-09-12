package com.resolveiq.detection.statistical;

import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.Detector;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.model.MetricSample;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Deterministic Exponentially Weighted Moving Average (EWMA) Detector (PRD §17).
 * Continuously weights recent samples more heavily than older samples, dynamically
 * adjusting the baseline and variance envelope to detect anomalies without lag.
 */
@Component
public class EwmaDetector implements Detector {

    public static final String DETECTOR_ID = "stat.ewma_baseline";

    @Override
    public String getId() {
        return DETECTOR_ID;
    }

    @Override
    public DetectorType getType() {
        return DetectorType.STATISTICAL;
    }

    @Override
    public DetectionEvaluationResult evaluate(List<MetricSample> samples, Map<String, Object> parameters) {
        int minSamples = getIntParam(parameters, "min_samples", 5);
        if (samples == null || samples.size() < minSamples) {
            return DetectionEvaluationResult.insufficientData(getId(), getType(), samples != null ? samples.size() : 0, minSamples);
        }

        double alpha = getDoubleParam(parameters, "alpha", 0.2); // Smoothing factor
        double sigmaMultiplier = getDoubleParam(parameters, "sigma_multiplier", 3.0);
        double criticalSigma = getDoubleParam(parameters, "critical_sigma_multiplier", 5.0);

        // Initialize EWMA mean and variance from the first sample
        double ewmaMean = samples.get(0).value();
        double ewmaVariance = 0.0;

        // Evolve EWMA up to second-to-last sample
        int historyCount = samples.size() - 1;
        for (int i = 1; i < historyCount; i++) {
            double val = samples.get(i).value();
            double prevMean = ewmaMean;
            ewmaMean = (alpha * val) + ((1.0 - alpha) * prevMean);
            ewmaVariance = (1.0 - alpha) * (ewmaVariance + alpha * Math.pow(val - prevMean, 2));
        }

        double stdDev = Math.sqrt(Math.max(ewmaVariance, 0.0));
        if (stdDev < 1e-6) {
            stdDev = Math.max(ewmaMean * 0.05, 0.01);
        }

        MetricSample latestSample = samples.get(samples.size() - 1);
        double observedValue = latestSample.value();
        double upperControlLimit = ewmaMean + (sigmaMultiplier * stdDev);
        double zScore = (observedValue - ewmaMean) / stdDev;

        if (observedValue >= upperControlLimit) {
            Severity severity = zScore >= criticalSigma ? Severity.CRITICAL : Severity.HIGH;
            String msg = String.format("EWMA breach: observed=%.2f breached UCL=%.2f (baseline=%.2f, stddev=%.2f, zScore=%.2f)",
                    observedValue, upperControlLimit, ewmaMean, stdDev, zScore);

            return DetectionEvaluationResult.breach(
                    getId(), getType(), observedValue, upperControlLimit, severity, samples.size(),
                    zScore, msg, Map.of("ewma_mean", ewmaMean, "ewma_stddev", stdDev, "ucl", upperControlLimit, "z_score", zScore)
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), observedValue, upperControlLimit, samples.size());
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultVal) {
        if (params != null && params.containsKey(key)) {
            Object v = params.get(key);
            if (v instanceof Number n) return n.doubleValue();
        }
        return defaultVal;
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultVal) {
        if (params != null && params.containsKey(key)) {
            Object v = params.get(key);
            if (v instanceof Number n) return n.intValue();
        }
        return defaultVal;
    }
}
