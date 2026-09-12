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
 * Deterministic Rolling-Window Standard Deviation (N-Sigma) Detector (PRD §17).
 * Computes moving mean and sample standard deviation across historical window,
 * triggering when the latest observed telemetry exceeds N standard deviations from the mean.
 */
@Component
public class RollingWindowStatisticalDetector implements Detector {

    public static final String DETECTOR_ID = "stat.rolling_window_sigma";

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
        int minSamples = getIntParam(parameters, "min_samples", 10);
        if (samples == null || samples.size() < minSamples) {
            return DetectionEvaluationResult.insufficientData(getId(), getType(), samples != null ? samples.size() : 0, minSamples);
        }

        double nSigma = getDoubleParam(parameters, "sigma_multiplier", 3.0); // 3-sigma default
        double criticalSigma = getDoubleParam(parameters, "critical_sigma_multiplier", 5.0);

        // Partition into historical baseline (all but last) and current test sample (last)
        int historySize = samples.size() - 1;
        double sum = 0.0;
        for (int i = 0; i < historySize; i++) {
            sum += samples.get(i).value();
        }
        double mean = sum / historySize;

        double sumSqDiff = 0.0;
        for (int i = 0; i < historySize; i++) {
            double diff = samples.get(i).value() - mean;
            sumSqDiff += diff * diff;
        }
        double variance = sumSqDiff / Math.max(1, historySize - 1);
        double stdDev = Math.sqrt(variance);

        MetricSample currentSample = samples.get(samples.size() - 1);
        double currentValue = currentSample.value();

        // Guard against zero variance (constant baseline)
        if (stdDev < 1e-6) {
            stdDev = Math.max(mean * 0.05, 0.01);
        }

        double zScore = (currentValue - mean) / stdDev;
        double upperThreshold = mean + (nSigma * stdDev);

        if (zScore >= nSigma) {
            Severity severity = zScore >= criticalSigma ? Severity.CRITICAL : Severity.HIGH;
            String msg = String.format("Metric value %.2f breached %.1f-sigma band (mean=%.2f, stddev=%.2f, threshold=%.2f, zScore=%.2f)",
                    currentValue, nSigma, mean, stdDev, upperThreshold, zScore);

            return DetectionEvaluationResult.breach(
                    getId(), getType(), currentValue, upperThreshold, severity, samples.size(),
                    zScore, msg, Map.of("mean", mean, "stddev", stdDev, "z_score", zScore, "upper_threshold", upperThreshold)
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), currentValue, upperThreshold, samples.size());
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
