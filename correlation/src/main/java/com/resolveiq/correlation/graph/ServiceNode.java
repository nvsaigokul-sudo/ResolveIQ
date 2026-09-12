package com.resolveiq.correlation.graph;

import com.resolveiq.common.correlation.ServiceCallEdge;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service Node in the Dynamic Dependency Graph (PRD §19).
 */
public class ServiceNode {

    private final String serviceId;
    private final Map<String, String> metadata = new ConcurrentHashMap<>();
    
    // Outgoing edges: services that this service calls (targetService -> ServiceCallEdge)
    private final Map<String, ServiceCallEdge> outgoingEdges = new ConcurrentHashMap<>();

    // Incoming edges: services that call this service (sourceService -> ServiceCallEdge)
    private final Map<String, ServiceCallEdge> incomingEdges = new ConcurrentHashMap<>();

    private volatile Instant lastActiveAt;

    public ServiceNode(String serviceId) {
        this.serviceId = serviceId;
        this.lastActiveAt = Instant.now();
    }

    public String getServiceId() {
        return serviceId;
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public void setMetadata(String key, String value) {
        if (key != null && value != null) {
            metadata.put(key, value);
        }
    }

    public Map<String, ServiceCallEdge> getOutgoingEdges() {
        return Collections.unmodifiableMap(outgoingEdges);
    }

    public Map<String, ServiceCallEdge> getIncomingEdges() {
        return Collections.unmodifiableMap(incomingEdges);
    }

    public void recordOutgoingCall(String targetService, boolean isError, double latencyMs, Instant timestamp) {
        outgoingEdges.compute(targetService, (k, existing) -> {
            if (existing == null) {
                return new ServiceCallEdge(serviceId, targetService, 1, isError ? 1 : 0, latencyMs, false, timestamp);
            } else {
                return existing.increment(isError, latencyMs, timestamp);
            }
        });
        updateLastActive(timestamp);
    }

    public void recordIncomingCall(String sourceService, boolean isError, double latencyMs, Instant timestamp) {
        incomingEdges.compute(sourceService, (k, existing) -> {
            if (existing == null) {
                return new ServiceCallEdge(sourceService, serviceId, 1, isError ? 1 : 0, latencyMs, false, timestamp);
            } else {
                return existing.increment(isError, latencyMs, timestamp);
            }
        });
        updateLastActive(timestamp);
    }

    public void markOutgoingEdgeAsCycle(String targetService, boolean isCycle) {
        outgoingEdges.computeIfPresent(targetService, (k, edge) -> edge.withCycleFlag(isCycle));
    }

    private void updateLastActive(Instant timestamp) {
        if (timestamp != null && (lastActiveAt == null || timestamp.isAfter(lastActiveAt))) {
            lastActiveAt = timestamp;
        }
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }
}
