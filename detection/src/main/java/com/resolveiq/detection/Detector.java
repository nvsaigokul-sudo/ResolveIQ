package com.resolveiq.detection;

import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.model.MetricSample;

import java.util.List;
import java.util.Map;

/**
 * Contract for all deterministic rule-based and statistical anomaly detectors (PRD §16, §17).
 */
public interface Detector {

    String getId();

    DetectorType getType();

    /**
     * Evaluates the window of metric samples against detector rules or statistical models.
     *
     * @param samples     chronological list of metric samples in the evaluation window
     * @param parameters  configuration parameters (thresholds, multipliers, alpha, etc.)
     * @return evaluation result with breach status, observed value, and diagnostic metadata
     */
    DetectionEvaluationResult evaluate(List<MetricSample> samples, Map<String, Object> parameters);
}
