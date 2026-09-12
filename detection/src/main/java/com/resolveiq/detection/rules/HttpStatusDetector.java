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
 * Deterministic HTTP Status Code Threshold Detector (PRD §16).
 * Flags surges in specific HTTP error response status codes (500, 502, 503, 504, 429).
 */
@Component
public class HttpStatusDetector implements Detector {

    public static final String DETECTOR_ID = "rule.http_status_code";

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

        String targetStatus = (String) (parameters != null ? parameters.getOrDefault("target_status", "503") : "503");
        double countThreshold = getDoubleParam(parameters, "count_threshold", 20.0);

        double matchingCount = 0.0;
        for (MetricSample sample : samples) {
            String statusCode = sample.dimensions().get("status");
            if (statusCode == null) {
                statusCode = sample.dimensions().get("http.status_code");
            }
            if (targetStatus.equals(statusCode)) {
                matchingCount += sample.value();
            }
        }

        if (matchingCount >= countThreshold) {
            Severity severity = "500".equals(targetStatus) || "503".equals(targetStatus) ? Severity.HIGH : Severity.MEDIUM;
            String msg = String.format("HTTP %s error count %.0f breached threshold %.0f over window",
                    targetStatus, matchingCount, countThreshold);

            return DetectionEvaluationResult.breach(
                    getId(), getType(), matchingCount, countThreshold, severity, samples.size(),
                    null, msg, Map.of("target_status", targetStatus, "observed_count", matchingCount)
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), matchingCount, countThreshold, samples.size());
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
