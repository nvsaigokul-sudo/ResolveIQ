package com.resolveiq.common.telemetry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * AnomalyDetected event payload conforming strictly to PRD Section 51.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnomalyDetectedPayload(
        @JsonProperty("anomaly_id") UUID anomalyId,
        @JsonProperty("fingerprint") String fingerprint,
        @JsonProperty("detector_id") String detectorId,
        @JsonProperty("detector_type") DetectorType detectorType,
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("metric_name") String metricName,
        @JsonProperty("current_value") double currentValue,
        @JsonProperty("threshold_value") double thresholdValue,
        @JsonProperty("severity") Severity severity,
        @JsonProperty("lifecycle_state") AnomalyLifecycleState lifecycleState,
        @JsonProperty("window_seconds") int windowSeconds,
        @JsonProperty("sample_count") int sampleCount,
        @JsonProperty("deviation_sigma") Double deviationSigma,
        @JsonProperty("details") Map<String, Object> details,
        @JsonProperty("detected_at") Instant detectedAt
) {
    public AnomalyDetectedPayload {
        if (details == null) details = Collections.emptyMap();
        if (detectedAt == null) detectedAt = Instant.now();
        if (anomalyId == null) anomalyId = UUID.randomUUID();
    }
}
