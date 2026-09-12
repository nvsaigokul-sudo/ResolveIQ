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
 * Adaptive Drift Protection Detector (PRD §17).
 * Protects against slow-onset creeping degradation (such as gradual memory leaks or thread pool exhaustion)
 * that gradually shifts rolling baselines and evades naive short-term threshold checks.
 */
@Component
public class AdaptiveDriftProtectionDetector implements Detector {

    public static final String DETECTOR_ID = "stat.adaptive_drift";

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
        int minSamples = getIntParam(parameters, "min_samples", 6);
        if (samples == null || samples.size() < minSamples) {
            return DetectionEvaluationResult.insufficientData(getId(), getType(), samples != null ? samples.size() : 0, minSamples);
        }

        double maxDriftPercent = getDoubleParam(parameters, "max_drift_percent", 30.0); // e.g. 30% drift above anchor
        double criticalDriftPercent = getDoubleParam(parameters, "critical_drift_percent", 60.0);

        // Partition window into anchor baseline (first third of samples) and current state (last third)
        int sliceSize = samples.size() / 3;
        double anchorSum = 0.0;
        for (int i = 0; i < sliceSize; i++) {
            anchorSum += samples.get(i).value();
        }
        double anchorBaseline = anchorSum / sliceSize;

        double currentSum = 0.0;
        for (int i = samples.size() - sliceSize; i < samples.size(); i++) {
            currentSum += samples.get(i).value();
        }
        double currentAverage = currentSum / sliceSize;

        // Verify monotonic upward trend using simple linear regression slope
        double n = samples.size();
        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumX2 = 0.0;
        for (int i = 0; i < samples.size(); i++) {
            double x = i;
            double y = samples.get(i).value();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        double slope = (n * sumXY - sumX * sumY) / Math.max(1e-9, (n * sumX2 - sumX * sumX));

        double denominator = Math.max(Math.abs(anchorBaseline), 1.0);
        double netDriftPercent = ((currentAverage - anchorBaseline) / denominator) * 100.0;

        // Monotonic positive drift exceeding allowed threshold
        if (slope > 0 && netDriftPercent >= maxDriftPercent) {
            Severity severity = netDriftPercent >= criticalDriftPercent ? Severity.CRITICAL : Severity.HIGH;
            String msg = String.format("Slow-onset adaptive drift detected: net increase %.1f%% over %d samples (anchor=%.2f, current=%.2f, slope=%.4f)",
                    netDriftPercent, samples.size(), anchorBaseline, currentAverage, slope);

            return DetectionEvaluationResult.breach(
                    getId(), getType(), currentAverage, anchorBaseline, severity, samples.size(),
                    null, msg, Map.of(
                            "anchor_baseline", anchorBaseline,
                            "current_average", currentAverage,
                            "net_drift_percent", netDriftPercent,
                            "regression_slope", slope
                    )
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), currentAverage, anchorBaseline, samples.size());
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
