package com.resolveiq.common.correlation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Breakdown of the 8 PRD §20 correlation signals with authoritative weights:
 * 1. Time Proximity (0.20)
 * 2. Service Topology Distance (0.25)
 * 3. Trace Linkage (0.20)
 * 4. Shared Failing Traces / Error Signatures (0.10)
 * 5. Deployment Timing Window (0.10)
 * 6. Common Infrastructure (0.05)
 * 7. Configuration Changes (0.05)
 * 8. Prior Incident History (0.05)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CorrelationSignalBreakdown(
        @JsonProperty("time_proximity_score") double timeProximityScore,
        @JsonProperty("topology_distance_score") double topologyDistanceScore,
        @JsonProperty("trace_linkage_score") double traceLinkageScore,
        @JsonProperty("shared_error_signature_score") double sharedErrorSignatureScore,
        @JsonProperty("deployment_timing_score") double deploymentTimingScore,
        @JsonProperty("common_infrastructure_score") double commonInfrastructureScore,
        @JsonProperty("config_change_score") double configChangeScore,
        @JsonProperty("prior_incident_history_score") double priorIncidentHistoryScore,
        @JsonProperty("composite_score") double compositeScore,
        @JsonProperty("confidence_tier") ConfidenceTier confidenceTier
) {
    public static final double WEIGHT_TIME_PROXIMITY = 0.20;
    public static final double WEIGHT_TOPOLOGY_DISTANCE = 0.25;
    public static final double WEIGHT_TRACE_LINKAGE = 0.20;
    public static final double WEIGHT_SHARED_ERROR_SIGNATURE = 0.10;
    public static final double WEIGHT_DEPLOYMENT_TIMING = 0.10;
    public static final double WEIGHT_COMMON_INFRASTRUCTURE = 0.05;
    public static final double WEIGHT_CONFIG_CHANGES = 0.05;
    public static final double WEIGHT_PRIOR_INCIDENT_HISTORY = 0.05;

    public static CorrelationSignalBreakdown compute(
            double timeProximity,
            double topologyDistance,
            double traceLinkage,
            double sharedErrorSignature,
            double deploymentTiming,
            double commonInfrastructure,
            double configChange,
            double priorIncidentHistory) {

        double composite = (timeProximity * WEIGHT_TIME_PROXIMITY)
                + (topologyDistance * WEIGHT_TOPOLOGY_DISTANCE)
                + (traceLinkage * WEIGHT_TRACE_LINKAGE)
                + (sharedErrorSignature * WEIGHT_SHARED_ERROR_SIGNATURE)
                + (deploymentTiming * WEIGHT_DEPLOYMENT_TIMING)
                + (commonInfrastructure * WEIGHT_COMMON_INFRASTRUCTURE)
                + (configChange * WEIGHT_CONFIG_CHANGES)
                + (priorIncidentHistory * WEIGHT_PRIOR_INCIDENT_HISTORY);

        // Clamp between 0.0 and 1.0
        composite = Math.max(0.0, Math.min(1.0, Math.round(composite * 1000.0) / 1000.0));
        ConfidenceTier tier = ConfidenceTier.fromScore(composite);

        return new CorrelationSignalBreakdown(
                timeProximity,
                topologyDistance,
                traceLinkage,
                sharedErrorSignature,
                deploymentTiming,
                commonInfrastructure,
                configChange,
                priorIncidentHistory,
                composite,
                tier
        );
    }
}
