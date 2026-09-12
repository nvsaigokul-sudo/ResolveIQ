package com.resolveiq.common.correlation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.resolveiq.common.telemetry.Severity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * IncidentCreated / IncidentCorrelated event payload conforming strictly to PRD §51.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CorrelatedIncidentPayload(
        @JsonProperty("incident_id") UUID incidentId,
        @JsonProperty("incident_fingerprint") String incidentFingerprint,
        @JsonProperty("title") String title,
        @JsonProperty("root_service_candidate") String rootServiceCandidate,
        @JsonProperty("severity") Severity severity,
        @JsonProperty("status") String status,
        @JsonProperty("primary_anomaly_id") UUID primaryAnomalyId,
        @JsonProperty("correlated_anomaly_ids") List<UUID> correlatedAnomalyIds,
        @JsonProperty("confidence_tier") ConfidenceTier confidenceTier,
        @JsonProperty("correlation_score") double correlationScore,
        @JsonProperty("signal_breakdown") CorrelationSignalBreakdown signalBreakdown,
        @JsonProperty("blast_radius") BlastRadiusResult blastRadius,
        @JsonProperty("candidate_origins") List<CandidateOriginScore> candidateOrigins,
        @JsonProperty("created_at") Instant createdAt
) {
}
