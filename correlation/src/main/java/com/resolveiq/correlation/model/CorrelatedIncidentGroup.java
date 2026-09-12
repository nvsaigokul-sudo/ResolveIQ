package com.resolveiq.correlation.model;

import com.resolveiq.common.correlation.BlastRadiusResult;
import com.resolveiq.common.correlation.CandidateOriginScore;
import com.resolveiq.common.correlation.ConfidenceTier;
import com.resolveiq.common.correlation.CorrelationSignalBreakdown;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.model.AnomalyRecord;

import java.time.Instant;
import java.util.*;

/**
 * In-flight correlated incident entity aggregating related anomalies (PRD §19, §20).
 */
public class CorrelatedIncidentGroup {

    private final UUID incidentId;
    private final UUID tenantId;
    private final UUID projectId;
    private final String environment;
    private final String incidentFingerprint;

    private String rootServiceCandidate;
    private final AnomalyRecord primaryAnomaly;
    private final List<AnomalyRecord> correlatedAnomalies = new ArrayList<>();

    private Severity severity;
    private String status; // "DETECTED", "INVESTIGATING", etc.
    private double compositeCorrelationScore;
    private ConfidenceTier confidenceTier;
    private CorrelationSignalBreakdown signalBreakdown;
    private BlastRadiusResult blastRadius;
    private List<CandidateOriginScore> candidateOrigins = new ArrayList<>();

    private final Instant createdAt;
    private Instant updatedAt;

    public CorrelatedIncidentGroup(
            UUID incidentId,
            UUID tenantId,
            UUID projectId,
            String environment,
            String incidentFingerprint,
            String rootServiceCandidate,
            AnomalyRecord primaryAnomaly,
            Severity severity,
            ConfidenceTier confidenceTier,
            double compositeCorrelationScore,
            CorrelationSignalBreakdown signalBreakdown,
            BlastRadiusResult blastRadius,
            List<CandidateOriginScore> candidateOrigins,
            Instant createdAt) {
        this.incidentId = incidentId;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.environment = environment;
        this.incidentFingerprint = incidentFingerprint;
        this.rootServiceCandidate = rootServiceCandidate;
        this.primaryAnomaly = primaryAnomaly;
        this.correlatedAnomalies.add(primaryAnomaly);
        this.severity = severity;
        this.status = "DETECTED";
        this.confidenceTier = confidenceTier;
        this.compositeCorrelationScore = compositeCorrelationScore;
        this.signalBreakdown = signalBreakdown;
        this.blastRadius = blastRadius;
        if (candidateOrigins != null) {
            this.candidateOrigins.addAll(candidateOrigins);
        }
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void addCorrelatedAnomaly(
            AnomalyRecord anomaly,
            CorrelationSignalBreakdown newBreakdown,
            String newRootService,
            BlastRadiusResult newBlastRadius,
            List<CandidateOriginScore> newOrigins) {

        correlatedAnomalies.add(anomaly);
        this.signalBreakdown = newBreakdown;
        this.compositeCorrelationScore = newBreakdown.compositeScore();
        this.confidenceTier = newBreakdown.confidenceTier();
        this.rootServiceCandidate = newRootService;
        this.blastRadius = newBlastRadius;
        this.candidateOrigins = newOrigins != null ? new ArrayList<>(newOrigins) : new ArrayList<>();

        // Escalate severity if incoming anomaly is more severe
        if (anomaly.getSeverity().ordinal() < this.severity.ordinal()) {
            this.severity = anomaly.getSeverity();
        }

        this.updatedAt = Instant.now();
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getIncidentFingerprint() {
        return incidentFingerprint;
    }

    public String getRootServiceCandidate() {
        return rootServiceCandidate;
    }

    public AnomalyRecord getPrimaryAnomaly() {
        return primaryAnomaly;
    }

    public List<AnomalyRecord> getCorrelatedAnomalies() {
        return Collections.unmodifiableList(correlatedAnomalies);
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getCompositeCorrelationScore() {
        return compositeCorrelationScore;
    }

    public ConfidenceTier getConfidenceTier() {
        return confidenceTier;
    }

    public CorrelationSignalBreakdown getSignalBreakdown() {
        return signalBreakdown;
    }

    public BlastRadiusResult getBlastRadius() {
        return blastRadius;
    }

    public List<CandidateOriginScore> getCandidateOrigins() {
        return Collections.unmodifiableList(candidateOrigins);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
