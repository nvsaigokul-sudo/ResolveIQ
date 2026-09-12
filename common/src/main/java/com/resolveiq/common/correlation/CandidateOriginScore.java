package com.resolveiq.common.correlation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Origin / root-cause candidate score for an anomaly in a correlated cluster (PRD §19, §20).
 * Combines topological depth, temporal anomaly onset precedence, and error severity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateOriginScore(
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("total_origin_score") double totalOriginScore,
        @JsonProperty("temporal_score") double temporalScore,
        @JsonProperty("topology_depth_score") double topologyDepthScore,
        @JsonProperty("severity_score") double severityScore,
        @JsonProperty("earliest_anomaly_time") Instant earliestAnomalyTime
) implements Comparable<CandidateOriginScore> {

    @Override
    public int compareTo(CandidateOriginScore o) {
        // Descending order of origin score
        return Double.compare(o.totalOriginScore, this.totalOriginScore);
    }
}
