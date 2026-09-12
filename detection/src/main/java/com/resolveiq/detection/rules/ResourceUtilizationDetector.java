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
 * Deterministic CPU and Memory Utilization Detector (PRD §16).
 * Triggers when compute resource utilization exceeds critical saturation thresholds.
 */
@Component
public class ResourceUtilizationDetector implements Detector {

    public static final String DETECTOR_ID = "rule.resource_utilization";

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

        double thresholdPercent = getDoubleParam(parameters, "threshold_percent", 85.0);
        double criticalPercent = getDoubleParam(parameters, "critical_percent", 95.0);

        double sum = 0.0;
        for (MetricSample s : samples) {
            sum += s.value();
        }
        double avgUtilization = sum / samples.size();

        if (avgUtilization >= thresholdPercent) {
            Severity severity = avgUtilization >= criticalPercent ? Severity.CRITICAL : Severity.HIGH;
            String msg = String.format("Resource utilization %.1f%% breached threshold %.1f%% over %d samples",
                    avgUtilization, thresholdPercent, samples.size());

            return DetectionEvaluationResult.breach(
                    getId(), getType(), avgUtilization, thresholdPercent, severity, samples.size(),
                    null, msg, Map.of("avg_utilization", avgUtilization, "threshold_percent", thresholdPercent)
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), avgUtilization, thresholdPercent, samples.size());
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
