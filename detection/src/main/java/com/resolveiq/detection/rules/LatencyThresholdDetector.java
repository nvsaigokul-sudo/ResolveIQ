package com.resolveiq.detection.rules;

import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.Detector;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.model.MetricSample;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Deterministic Latency Percentile Detector (PRD §16).
 * Computes exact p95 or p99 latency from sample distributions and checks against SLA thresholds.
 */
@Component
public class LatencyThresholdDetector implements Detector {

    public static final String DETECTOR_ID = "rule.latency_threshold";

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
        int minSamples = getIntParam(parameters, "min_samples", 5);
        if (samples == null || samples.size() < minSamples) {
            return DetectionEvaluationResult.insufficientData(getId(), getType(), samples != null ? samples.size() : 0, minSamples);
        }

        double percentile = getDoubleParam(parameters, "percentile", 95.0); // e.g. 95 or 99
        double thresholdMs = getDoubleParam(parameters, "threshold_ms", 500.0);
        double criticalThresholdMs = getDoubleParam(parameters, "critical_threshold_ms", 1000.0);

        List<Double> values = new ArrayList<>(samples.size());
        for (MetricSample sample : samples) {
            values.add(sample.value());
        }
        Collections.sort(values);

        // Nearest-rank percentile calculation
        int rank = (int) Math.ceil((percentile / 100.0) * values.size());
        int index = Math.min(Math.max(rank - 1, 0), values.size() - 1);
        double calculatedPercentileVal = values.get(index);

        if (calculatedPercentileVal >= thresholdMs) {
            Severity severity = calculatedPercentileVal >= criticalThresholdMs ? Severity.CRITICAL : Severity.HIGH;
            String msg = String.format("p%.0f latency %.2fms breached SLA threshold %.2fms over %d requests",
                    percentile, calculatedPercentileVal, thresholdMs, samples.size());

            return DetectionEvaluationResult.breach(
                    getId(), getType(), calculatedPercentileVal, thresholdMs, severity, samples.size(),
                    null, msg, Map.of("percentile", percentile, "observed_ms", calculatedPercentileVal, "threshold_ms", thresholdMs)
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), calculatedPercentileVal, thresholdMs, samples.size());
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
