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
 * Deterministic Availability & Uptime Detector (PRD §16).
 * Triggers when service uptime / successful request ratio drops below SLA commitment.
 */
@Component
public class AvailabilityDetector implements Detector {

    public static final String DETECTOR_ID = "rule.availability";

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

        double minAvailabilityPercent = getDoubleParam(parameters, "min_availability_percent", 99.0);
        double criticalAvailabilityPercent = getDoubleParam(parameters, "critical_availability_percent", 95.0);

        double sum = 0.0;
        for (MetricSample s : samples) {
            sum += s.value();
        }
        double avgAvailability = sum / samples.size();

        // If values are expressed as 0.0-1.0 fraction instead of 0-100%, normalize
        if (avgAvailability <= 1.0 && minAvailabilityPercent > 1.0) {
            avgAvailability *= 100.0;
        }

        if (avgAvailability < minAvailabilityPercent) {
            Severity severity = avgAvailability < criticalAvailabilityPercent ? Severity.CRITICAL : Severity.HIGH;
            String msg = String.format("Service availability %.2f%% dropped below SLA commitment %.2f%% over %d samples",
                    avgAvailability, minAvailabilityPercent, samples.size());

            return DetectionEvaluationResult.breach(
                    getId(), getType(), avgAvailability, minAvailabilityPercent, severity, samples.size(),
                    null, msg, Map.of("avg_availability", avgAvailability, "sla_target", minAvailabilityPercent)
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), avgAvailability, minAvailabilityPercent, samples.size());
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
