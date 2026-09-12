package com.resolveiq.common.correlation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Directed service-to-service dependency edge in the Dynamic Dependency Graph (PRD §19).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceCallEdge(
        @JsonProperty("source_service") String sourceService,
        @JsonProperty("target_service") String targetService,
        @JsonProperty("call_count") long callCount,
        @JsonProperty("error_count") long errorCount,
        @JsonProperty("avg_latency_ms") double avgLatencyMs,
        @JsonProperty("is_cycle_edge") boolean isCycleEdge,
        @JsonProperty("last_observed_at") Instant lastObservedAt
) {
    public ServiceCallEdge increment(boolean isError, double latencyMs, Instant observedAt) {
        long newCallCount = this.callCount + 1;
        long newErrorCount = isError ? this.errorCount + 1 : this.errorCount;
        double newAvgLatency = ((this.avgLatencyMs * this.callCount) + latencyMs) / newCallCount;
        Instant newObservedAt = observedAt != null && (this.lastObservedAt == null || observedAt.isAfter(this.lastObservedAt))
                ? observedAt : this.lastObservedAt;
        return new ServiceCallEdge(
                sourceService, targetService, newCallCount, newErrorCount,
                newAvgLatency, isCycleEdge, newObservedAt
        );
    }

    public ServiceCallEdge withCycleFlag(boolean cycle) {
        return new ServiceCallEdge(
                sourceService, targetService, callCount, errorCount,
                avgLatencyMs, cycle, lastObservedAt
        );
    }
}
