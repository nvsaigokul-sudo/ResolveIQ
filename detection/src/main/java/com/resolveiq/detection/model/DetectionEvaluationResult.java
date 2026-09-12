package com.resolveiq.detection.model;

import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;

import java.util.Collections;
import java.util.Map;

/**
 * Result of evaluating a metric window against a detector.
 */
public record DetectionEvaluationResult(
        boolean isBreached,
        String detectorId,
        DetectorType detectorType,
        double observedValue,
        double thresholdValue,
        Severity severity,
        int sampleCount,
        Double deviationSigma,
        String message,
        Map<String, Object> context
) {
    public static DetectionEvaluationResult ok(String detectorId, DetectorType detectorType, double observed, double threshold, int samples) {
        return new DetectionEvaluationResult(
                false, detectorId, detectorType, observed, threshold, Severity.INFO, samples, null, "Normal", Collections.emptyMap()
        );
    }

    public static DetectionEvaluationResult breach(
            String detectorId, DetectorType detectorType, double observed, double threshold,
            Severity severity, int samples, Double deviationSigma, String message, Map<String, Object> context) {
        return new DetectionEvaluationResult(
                true, detectorId, detectorType, observed, threshold, severity, samples, deviationSigma, message,
                context != null ? context : Collections.emptyMap()
        );
    }

    public static DetectionEvaluationResult insufficientData(String detectorId, DetectorType detectorType, int sampleCount, int requiredCount) {
        return new DetectionEvaluationResult(
                false, detectorId, detectorType, 0.0, 0.0, Severity.INFO, sampleCount, null,
                "Insufficient telemetry samples (" + sampleCount + " < " + requiredCount + ")", Collections.emptyMap()
        );
    }
}
