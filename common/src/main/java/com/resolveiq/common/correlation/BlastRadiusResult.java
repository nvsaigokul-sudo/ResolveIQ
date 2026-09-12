package com.resolveiq.common.correlation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * Calculated blast radius of a failing service across the dependency topology (PRD §19).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlastRadiusResult(
        @JsonProperty("root_service") String rootService,
        @JsonProperty("impacted_services") Set<String> impactedServices,
        @JsonProperty("direct_downstream_count") int directDownstreamCount,
        @JsonProperty("total_downstream_count") int totalDownstreamCount,
        @JsonProperty("system_impact_percent") double systemImpactPercent
) {
}
