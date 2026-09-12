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
 * Deterministic Queue Depth & Backlog Lag Detector (PRD §16).
 * Detects consumer lag and queue buildup exceeding buffer capacity thresholds.
 */
@Component
public class QueueDepthDetector implements Detector {

    public static final String DETECTOR_ID = "rule.queue_depth";

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
        int minSamples = getIntParam(parameters, "min_samples", 2);
        if (samples == null || samples.size() < minSamples) {
            return DetectionEvaluationResult.insufficientData(getId(), getType(), samples != null ? samples.size() : 0, minSamples);
        }

        double threshold = getDoubleParam(parameters, "depth_threshold", 1000.0);
        double criticalThreshold = getDoubleParam(parameters, "critical_depth_threshold", 5000.0);

        // Get latest sample and check trajectory
        MetricSample latest = samples.get(samples.size() - 1);
        double currentDepth = latest.value();

        if (currentDepth >= threshold) {
            Severity severity = currentDepth >= criticalThreshold ? Severity.CRITICAL : Severity.HIGH;
            String msg = String.format("Queue depth %.0f exceeded capacity threshold %.0f",
                    currentDepth, threshold);

            return DetectionEvaluationResult.breach(
                    getId(), getType(), currentDepth, threshold, severity, samples.size(),
                    null, msg, Map.of("current_depth", currentDepth, "threshold", threshold)
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), currentDepth, threshold, samples.size());
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
