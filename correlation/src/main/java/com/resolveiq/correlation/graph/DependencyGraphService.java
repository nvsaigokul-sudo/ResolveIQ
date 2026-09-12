package com.resolveiq.correlation.graph;

import com.resolveiq.common.correlation.BlastRadiusResult;
import com.resolveiq.common.correlation.CandidateOriginScore;
import com.resolveiq.detection.model.AnomalyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-Tenant Dynamic Dependency Graph Service (PRD §19).
 * Manages isolated dependency graphs per tenant, ingests spans,
 * and computes topological distance, blast radius, and candidate origins.
 */
@Service
public class DependencyGraphService {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphService.class);

    // Tenant isolation: Map<tenantId, TenantDependencyGraph>
    private final Map<UUID, TenantDependencyGraph> tenantGraphs = new ConcurrentHashMap<>();

    public TenantDependencyGraph getOrCreateGraph(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        return tenantGraphs.computeIfAbsent(tenantId, TenantDependencyGraph::new);
    }

    public Optional<TenantDependencyGraph> getGraph(UUID tenantId) {
        return Optional.ofNullable(tenantGraphs.get(tenantId));
    }

    public void recordServiceCall(
            UUID tenantId,
            String sourceService,
            String targetService,
            boolean isError,
            double latencyMs,
            Instant timestamp) {
        getOrCreateGraph(tenantId).recordServiceCall(sourceService, targetService, isError, latencyMs, timestamp);
    }

    public void ingestSpan(
            UUID tenantId,
            String traceId,
            String spanId,
            String parentSpanId,
            String serviceName,
            boolean isError,
            double durationMs,
            Instant timestamp) {
        getOrCreateGraph(tenantId).ingestSpan(
                traceId, spanId, parentSpanId, serviceName, isError, durationMs, timestamp);
    }

    public int getShortestDistance(UUID tenantId, String serviceA, String serviceB) {
        TenantDependencyGraph graph = tenantGraphs.get(tenantId);
        if (graph == null) {
            return -1;
        }
        return graph.getShortestDistance(serviceA, serviceB);
    }

    public BlastRadiusResult calculateBlastRadius(UUID tenantId, String rootService) {
        TenantDependencyGraph graph = tenantGraphs.get(tenantId);
        if (graph == null) {
            return new BlastRadiusResult(rootService, Collections.emptySet(), 0, 0, 0.0);
        }
        return graph.calculateBlastRadius(rootService);
    }

    public List<CandidateOriginScore> scoreOriginCandidates(UUID tenantId, List<AnomalyRecord> anomalies) {
        TenantDependencyGraph graph = tenantGraphs.get(tenantId);
        if (graph == null) {
            return Collections.emptyList();
        }
        return graph.scoreOriginCandidates(anomalies);
    }

    public void clear(UUID tenantId) {
        TenantDependencyGraph graph = tenantGraphs.get(tenantId);
        if (graph != null) {
            graph.clear();
        }
    }

    public void clearAll() {
        tenantGraphs.clear();
    }
}
