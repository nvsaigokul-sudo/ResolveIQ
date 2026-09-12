package com.resolveiq.detection.rules;

import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.Detector;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.model.MetricSample;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Deterministic Rule-Based 5xx Error Rate Detector (PRD §16).
 * Triggers when the error rate exceeds threshold percentage over evaluation window W.
 */
@Component
public class ErrorRateDetector implements Detector {

    public static final String DETECTOR_ID = "rule.error_rate_5xx";

    @Override
    public String getId() {
        return DETECTOR_ID;
    }

    @Override
    public DetectorType getType() {
        return DetectorType.RULE_BASED;
    }

    @Override
    public DetectionEvaluationResult evaluate(List<MetricSample> samples, Map<String, Object> parameters) {
        int minSamples = getIntParam(parameters, "min_samples", 3);
        if (samples == null || samples.size() < minSamples) {
            return DetectionEvaluationResult.insufficientData(getId(), getType(), samples != null ? samples.size() : 0, minSamples);
        }

        double thresholdPercent = getDoubleParam(parameters, "threshold_percent", 5.0);
        double criticalThresholdPercent = getDoubleParam(parameters, "critical_threshold_percent", 10.0);

        // Calculate average error rate across the window
        double sum = 0.0;
        for (MetricSample sample : samples) {
            sum += sample.value();
        }
        double avgErrorRate = sum / samples.size();

        if (avgErrorRate >= thresholdPercent) {
            Severity severity = avgErrorRate >= criticalThresholdPercent ? Severity.CRITICAL : Severity.HIGH;
            String message = String.format("5xx error rate %.2f%% breached threshold %.2f%% over %d samples",
                    avgErrorRate, thresholdPercent, samples.size());

            return DetectionEvaluationResult.breach(
                    getId(), getType(), avgErrorRate, thresholdPercent, severity, samples.size(),
                    null, message, Map.of("avg_error_rate_percent", avgErrorRate, "threshold_percent", thresholdPercent)
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), avgErrorRate, thresholdPercent, samples.size());
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
