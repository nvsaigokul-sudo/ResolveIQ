package com.resolveiq.correlation.graph;

import com.resolveiq.common.correlation.BlastRadiusResult;
import com.resolveiq.common.correlation.CandidateOriginScore;
import com.resolveiq.common.correlation.ServiceCallEdge;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.model.AnomalyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe Dynamic Dependency Graph for a single tenant (PRD §19).
 * Constructs topology from distributed trace spans, detects cycles,
 * calculates blast radius, and computes candidate root-cause origin scores.
 */
public class TenantDependencyGraph {

    private static final Logger log = LoggerFactory.getLogger(TenantDependencyGraph.class);

    private final UUID tenantId;
    private final Map<String, ServiceNode> nodes = new ConcurrentHashMap<>();

    // Span buffer for trace correlation: traceId -> Map<spanId, TraceSpanEntry>
    private final Map<String, Map<String, TraceSpanEntry>> spanBuffer = new ConcurrentHashMap<>();
    private static final Duration SPAN_BUFFER_TTL = Duration.ofMinutes(10);
    private final Map<String, Instant> traceLastUpdated = new ConcurrentHashMap<>();

    public record TraceSpanEntry(
            String spanId,
            String parentSpanId,
            String serviceName,
            boolean isError,
            double durationMs,
            Instant timestamp
    ) {}

    public TenantDependencyGraph(UUID tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public ServiceNode getOrCreateNode(String serviceId) {
        return nodes.computeIfAbsent(serviceId, ServiceNode::new);
    }

    public Optional<ServiceNode> getNode(String serviceId) {
        return Optional.ofNullable(nodes.get(serviceId));
    }

    public Set<String> getAllServiceIds() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    /**
     * Records a direct service-to-service call edge.
     */
    public void recordServiceCall(String sourceService, String targetService, boolean isError, double latencyMs, Instant timestamp) {
        if (sourceService == null || targetService == null || sourceService.equalsIgnoreCase(targetService)) {
            return;
        }

        ServiceNode sourceNode = getOrCreateNode(sourceService);
        ServiceNode targetNode = getOrCreateNode(targetService);

        sourceNode.recordOutgoingCall(targetService, isError, latencyMs, timestamp);
        targetNode.recordIncomingCall(sourceService, isError, latencyMs, timestamp);

        // Check if this newly added edge introduces a cycle
        checkAndMarkCycles();
    }

    /**
     * Ingests a distributed trace span and establishes service-to-service call edges.
     */
    public void ingestSpan(
            String traceId,
            String spanId,
            String parentSpanId,
            String serviceName,
            boolean isError,
            double durationMs,
            Instant timestamp) {

        if (traceId == null || spanId == null || serviceName == null) {
            return;
        }

        getOrCreateNode(serviceName);

        Instant now = timestamp != null ? timestamp : Instant.now();
        traceLastUpdated.put(traceId, now);

        Map<String, TraceSpanEntry> traceSpans = spanBuffer.computeIfAbsent(traceId, k -> new ConcurrentHashMap<>());
        TraceSpanEntry currentEntry = new TraceSpanEntry(spanId, parentSpanId, serviceName, isError, durationMs, now);
        traceSpans.put(spanId, currentEntry);

        // 1. If parentSpanId is present, check if parent span is already buffered
        if (parentSpanId != null && !parentSpanId.isBlank()) {
            TraceSpanEntry parentEntry = traceSpans.get(parentSpanId);
            if (parentEntry != null && !parentEntry.serviceName().equalsIgnoreCase(serviceName)) {
                // Cross-service call: parent.service -> current.service
                recordServiceCall(parentEntry.serviceName(), serviceName, isError, durationMs, now);
            }
        }

        // 2. Check if any already buffered child spans have current span as parent
        for (TraceSpanEntry other : traceSpans.values()) {
            if (spanId.equals(other.parentSpanId()) && !serviceName.equalsIgnoreCase(other.serviceName())) {
                recordServiceCall(serviceName, other.serviceName(), other.isError(), other.durationMs(), other.timestamp());
            }
        }

        // Periodic pruning of old trace buffers
        if (spanBuffer.size() > 5000) {
            pruneOldSpanBuffers();
        }
    }

    private void pruneOldSpanBuffers() {
        Instant cutoff = Instant.now().minus(SPAN_BUFFER_TTL);
        traceLastUpdated.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                spanBuffer.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Computes the shortest graph distance (hop count) between two services.
     * Evaluates both directed and reverse connections.
     *
     * @return 0 if same service, 1 for direct call, 2+ for multi-hop, -1 if unreachable.
     */
    public int getShortestDistance(String serviceA, String serviceB) {
        if (serviceA == null || serviceB == null) return -1;
        if (serviceA.equalsIgnoreCase(serviceB)) return 0;
        if (!nodes.containsKey(serviceA) || !nodes.containsKey(serviceB)) return -1;

        Queue<String> queue = new ArrayDeque<>();
        Map<String, Integer> distances = new HashMap<>();

        queue.add(serviceA);
        distances.put(serviceA, 0);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = distances.get(current);

            ServiceNode node = nodes.get(current);
            if (node == null) continue;

            // Combine both outgoing and incoming neighbors for topology proximity
            Set<String> neighbors = new HashSet<>(node.getOutgoingEdges().keySet());
            neighbors.addAll(node.getIncomingEdges().keySet());

            for (String neighbor : neighbors) {
                if (neighbor.equalsIgnoreCase(serviceB)) {
                    return currentDist + 1;
                }
                if (!distances.containsKey(neighbor)) {
                    distances.put(neighbor, currentDist + 1);
                    queue.add(neighbor);
                }
            }
        }

        return -1; // Disconnected components
    }

    /**
     * Detects cycles in the directed graph using DFS recursion-stack tracking,
     * and safely marks cycle edges with is_cycle_edge = true.
     */
    public synchronized void checkAndMarkCycles() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String serviceId : nodes.keySet()) {
            if (!visited.contains(serviceId)) {
                dfsCycleDetect(serviceId, visited, recursionStack);
            }
        }
    }

    private void dfsCycleDetect(String current, Set<String> visited, Set<String> recursionStack) {
        visited.add(current);
        recursionStack.add(current);

        ServiceNode node = nodes.get(current);
        if (node != null) {
            for (Map.Entry<String, ServiceCallEdge> entry : node.getOutgoingEdges().entrySet()) {
                String neighbor = entry.getKey();
                if (recursionStack.contains(neighbor)) {
                    // Cycle detected: current -> neighbor
                    node.markOutgoingEdgeAsCycle(neighbor, true);
                    log.debug("Detected circular service dependency: {} -> {}", current, neighbor);
                } else if (!visited.contains(neighbor)) {
                    dfsCycleDetect(neighbor, visited, recursionStack);
                }
            }
        }

        recursionStack.remove(current);
    }

    /**
     * Calculates the blast radius of a failing root service.
     * Identifies all direct and transitive callers dependent on the failing service.
     */
    public BlastRadiusResult calculateBlastRadius(String rootService) {
        if (rootService == null || !nodes.containsKey(rootService)) {
            return new BlastRadiusResult(rootService, Collections.emptySet(), 0, 0, 0.0);
        }

        Set<String> impactedServices = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        // Direct callers that depend on rootService
        ServiceNode rootNode = nodes.get(rootService);
        Set<String> directCallers = rootNode.getIncomingEdges().keySet();
        int directCount = directCallers.size();

        for (String caller : directCallers) {
            queue.add(caller);
            visited.add(caller);
            impactedServices.add(caller);
        }

        // BFS traversal up the caller hierarchy (safely handling any circular dependencies)
        while (!queue.isEmpty()) {
            String current = queue.poll();
            ServiceNode currentNode = nodes.get(current);
            if (currentNode == null) continue;

            for (String upstreamCaller : currentNode.getIncomingEdges().keySet()) {
                if (!visited.contains(upstreamCaller) && !upstreamCaller.equalsIgnoreCase(rootService)) {
                    visited.add(upstreamCaller);
                    impactedServices.add(upstreamCaller);
                    queue.add(upstreamCaller);
                }
            }
        }

        int totalServices = Math.max(1, nodes.size());
        double systemImpactPercent = ((double) impactedServices.size() / totalServices) * 100.0;
        systemImpactPercent = Math.round(systemImpactPercent * 10.0) / 10.0;

        return new BlastRadiusResult(
                rootService,
                Collections.unmodifiableSet(impactedServices),
                directCount,
                impactedServices.size(),
                systemImpactPercent
        );
    }

    /**
     * Evaluates and scores candidate origin/root-cause services for a cluster of anomalies (PRD §19).
     * Combines topological depth, temporal precedence, and severity.
     */
    public List<CandidateOriginScore> scoreOriginCandidates(List<AnomalyRecord> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) {
            return Collections.emptyList();
        }

        // Group anomalies by serviceId and determine earliest timestamp and max severity
        Map<String, Instant> serviceEarliestTime = new HashMap<>();
        Map<String, Severity> serviceMaxSeverity = new HashMap<>();

        Instant globalEarliest = Instant.MAX;

        for (AnomalyRecord a : anomalies) {
            String svc = a.getServiceId();
            Instant t = a.getFirstDetectedAt();

            if (t.isBefore(globalEarliest)) {
                globalEarliest = t;
            }

            serviceEarliestTime.compute(svc, (k, existing) -> existing == null || t.isBefore(existing) ? t : existing);
            serviceMaxSeverity.compute(svc, (k, existing) -> {
                if (existing == null) return a.getSeverity();
                return a.getSeverity().ordinal() < existing.ordinal() ? a.getSeverity() : existing;
            });
        }

        List<CandidateOriginScore> candidateScores = new ArrayList<>();
        Set<String> failingServices = serviceEarliestTime.keySet();

        for (String svc : failingServices) {
            Instant svcTime = serviceEarliestTime.get(svc);
            Severity severity = serviceMaxSeverity.get(svc);

            // 1. Temporal Score: earliest anomaly gets 1.0, decays with time offset
            long deltaSeconds = Math.max(0, Duration.between(globalEarliest, svcTime).toSeconds());
            double temporalScore = Math.exp(-deltaSeconds / 60.0); // 1-minute decay constant

            // 2. Topology Depth Score: how many other failing services depend on this service?
            // If others call this service, this service is downstream/callee (the root dependency!).
            int dependentFailingCount = 0;
            BlastRadiusResult blast = calculateBlastRadius(svc);
            for (String otherFailing : failingServices) {
                if (!otherFailing.equalsIgnoreCase(svc) && blast.impactedServices().contains(otherFailing)) {
                    dependentFailingCount++;
                }
            }
            int totalOtherFailing = Math.max(1, failingServices.size() - 1);
            double topologyScore = failingServices.size() == 1 ? 1.0 : (double) dependentFailingCount / totalOtherFailing;

            // 3. Severity Score
            double severityScore = switch (severity) {
                case CRITICAL -> 1.0;
                case HIGH -> 0.8;
                case MEDIUM -> 0.5;
                case LOW -> 0.3;
                case INFO -> 0.1;
            };

            // Weighted composite origin score (PRD §19): 0.40 Temporal + 0.35 Topology + 0.25 Severity
            double totalScore = (0.40 * temporalScore) + (0.35 * topologyScore) + (0.25 * severityScore);
            totalScore = Math.round(totalScore * 1000.0) / 1000.0;

            candidateScores.add(new CandidateOriginScore(
                    svc, totalScore, temporalScore, topologyScore, severityScore, svcTime));
        }

        Collections.sort(candidateScores);
        return candidateScores;
    }

    public void clear() {
        nodes.clear();
        spanBuffer.clear();
        traceLastUpdated.clear();
    }
}
