package com.resolveiq.detection.model;

import com.resolveiq.common.telemetry.AnomalyLifecycleState;
import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable tracking record for an active or resolved anomaly within the detection engine.
 */
public class AnomalyRecord {
    private final UUID anomalyId;
    private final String fingerprint;
    private final UUID tenantId;
    private final UUID projectId;
    private final String serviceId;
    private final String metricName;
    private final String environment;
    private final String detectorId;
    private final DetectorType detectorType;

    private Severity severity;
    private AnomalyLifecycleState lifecycleState;
    private double initialValue;
    private double lastObservedValue;
    private double thresholdValue;
    private Double deviationSigma;
    private Instant firstDetectedAt;
    private Instant lastEvaluatedAt;
    private Instant resolvedAt;
    private int consecutiveBreachCount;
    private int consecutiveNormalCount;
    private Map<String, Object> details;

    public AnomalyRecord(
            UUID anomalyId,
            String fingerprint,
            UUID tenantId,
            UUID projectId,
            String serviceId,
            String metricName,
            String environment,
            String detectorId,
            DetectorType detectorType,
            Severity severity,
            AnomalyLifecycleState lifecycleState,
            double initialValue,
            double thresholdValue,
            Double deviationSigma,
            Instant detectedAt,
            Map<String, Object> details) {
        this.anomalyId = anomalyId != null ? anomalyId : UUID.randomUUID();
        this.fingerprint = fingerprint;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.serviceId = serviceId;
        this.metricName = metricName;
        this.environment = environment != null ? environment : "production";
        this.detectorId = detectorId;
        this.detectorType = detectorType;
        this.severity = severity;
        this.lifecycleState = lifecycleState;
        this.initialValue = initialValue;
        this.lastObservedValue = initialValue;
        this.thresholdValue = thresholdValue;
        this.deviationSigma = deviationSigma;
        this.firstDetectedAt = detectedAt != null ? detectedAt : Instant.now();
        this.lastEvaluatedAt = this.firstDetectedAt;
        this.consecutiveBreachCount = 1;
        this.consecutiveNormalCount = 0;
        this.details = details != null ? details : Collections.emptyMap();
    }

    public UUID getAnomalyId() { return anomalyId; }
    public String getFingerprint() { return fingerprint; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getServiceId() { return serviceId; }
    public String getMetricName() { return metricName; }
    public String getEnvironment() { return environment; }
    public String getDetectorId() { return detectorId; }
    public DetectorType getDetectorType() { return detectorType; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public AnomalyLifecycleState getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(AnomalyLifecycleState lifecycleState) { this.lifecycleState = lifecycleState; }
    public double getInitialValue() { return initialValue; }
    public double getLastObservedValue() { return lastObservedValue; }
    public void setLastObservedValue(double lastObservedValue) { this.lastObservedValue = lastObservedValue; }
    public double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(double thresholdValue) { this.thresholdValue = thresholdValue; }
    public Double getDeviationSigma() { return deviationSigma; }
    public void setDeviationSigma(Double deviationSigma) { this.deviationSigma = deviationSigma; }
    public Instant getFirstDetectedAt() { return firstDetectedAt; }
    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(Instant lastEvaluatedAt) { this.lastEvaluatedAt = lastEvaluatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public int getConsecutiveBreachCount() { return consecutiveBreachCount; }
    public void incrementConsecutiveBreachCount() { this.consecutiveBreachCount++; this.consecutiveNormalCount = 0; }
    public int getConsecutiveNormalCount() { return consecutiveNormalCount; }
    public void incrementConsecutiveNormalCount() { this.consecutiveNormalCount++; }
    public void resetConsecutiveNormalCount() { this.consecutiveNormalCount = 0; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details != null ? details : Collections.emptyMap(); }
}
