package com.resolveiq.correlation.engine;

import com.resolveiq.common.correlation.CorrelationSignalBreakdown;
import com.resolveiq.correlation.graph.DependencyGraphService;
import com.resolveiq.correlation.model.ConfigChangeEvent;
import com.resolveiq.correlation.model.DeploymentEvent;
import com.resolveiq.correlation.model.ServiceInfrastructureMetadata;
import com.resolveiq.detection.model.AnomalyRecord;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Deterministic Multi-Signal Correlation Evaluator (PRD §20).
 * Evaluates the 8 PRD signals with strict mathematical scoring and weights.
 */
@Component
public class CorrelationSignalEvaluator {

    private final DependencyGraphService graphService;

    // Half-life decay constant for time proximity (300 seconds = 5 minutes)
    private static final double TIME_DECAY_CONSTANT_SECONDS = 300.0;
    private static final long CORRELATION_HORIZON_SECONDS = 900; // 15 minutes

    public CorrelationSignalEvaluator(DependencyGraphService graphService) {
        this.graphService = graphService;
    }

    public CorrelationSignalBreakdown evaluate(
            AnomalyRecord anomalyA,
            AnomalyRecord anomalyB,
            List<DeploymentEvent> deployments,
            List<ConfigChangeEvent> configChanges,
            Map<String, ServiceInfrastructureMetadata> infraMetadata,
            Set<String> coFailureHistory) {

        UUID tenantId = anomalyA.getTenantId();

        // 1. Time Proximity (0.20)
        double timeProximity = evaluateTimeProximity(anomalyA.getFirstDetectedAt(), anomalyB.getFirstDetectedAt());

        // 2. Topology Distance (0.25)
        double topologyDistance = evaluateTopologyDistance(tenantId, anomalyA.getServiceId(), anomalyB.getServiceId());

        // 3. Trace Linkage (0.20)
        double traceLinkage = evaluateTraceLinkage(anomalyA, anomalyB);

        // 4. Shared Failing Traces / Error Signatures (0.10)
        double sharedErrorSignature = evaluateErrorSignature(anomalyA, anomalyB);

        // 5. Deployment Timing (0.10)
        double deploymentTiming = evaluateDeploymentTiming(anomalyA, anomalyB, deployments, tenantId);

        // 6. Common Infrastructure (0.05)
        double commonInfrastructure = evaluateInfrastructure(anomalyA.getServiceId(), anomalyB.getServiceId(), infraMetadata);

        // 7. Configuration Changes (0.05)
        double configChange = evaluateConfigChanges(anomalyA, anomalyB, configChanges, tenantId);

        // 8. Prior Incident History (0.05)
        double priorHistory = evaluatePriorHistory(anomalyA.getServiceId(), anomalyB.getServiceId(), coFailureHistory);

        return CorrelationSignalBreakdown.compute(
                timeProximity,
                topologyDistance,
                traceLinkage,
                sharedErrorSignature,
                deploymentTiming,
                commonInfrastructure,
                configChange,
                priorHistory
        );
    }

    private double evaluateTimeProximity(Instant tA, Instant tB) {
        if (tA == null || tB == null) return 0.0;
        long deltaSec = Math.abs(Duration.between(tA, tB).toSeconds());
        if (deltaSec > CORRELATION_HORIZON_SECONDS) {
            return 0.0;
        }
        return Math.exp(-deltaSec / TIME_DECAY_CONSTANT_SECONDS);
    }

    private double evaluateTopologyDistance(UUID tenantId, String serviceA, String serviceB) {
        if (serviceA.equalsIgnoreCase(serviceB)) {
            return 1.0;
        }
        int distance = graphService.getShortestDistance(tenantId, serviceA, serviceB);
        return switch (distance) {
            case 1 -> 0.90; // Direct caller/callee dependency
            case 2 -> 0.70; // 2 hops
            case 3 -> 0.40; // 3 hops
            case -1 -> 0.00; // Disconnected
            default -> 0.15; // 4 or more hops
        };
    }

    private double evaluateTraceLinkage(AnomalyRecord a, AnomalyRecord b) {
        Object traceA = a.getDetails() != null ? a.getDetails().get("trace_id") : null;
        Object traceB = b.getDetails() != null ? b.getDetails().get("trace_id") : null;

        if (traceA != null && traceB != null && traceA.toString().equalsIgnoreCase(traceB.toString())) {
            return 1.0; // Exact shared failing trace exemplar
        }

        Object sharedTracesA = a.getDetails() != null ? a.getDetails().get("shared_traces") : null;
        if (sharedTracesA instanceof Collection<?> coll && traceB != null && coll.contains(traceB.toString())) {
            return 0.85;
        }

        return 0.0;
    }

    private double evaluateErrorSignature(AnomalyRecord a, AnomalyRecord b) {
        Object statusA = a.getDetails() != null ? a.getDetails().get("status") : null;
        Object statusB = b.getDetails() != null ? b.getDetails().get("status") : null;

        if (statusA != null && statusB != null) {
            if (statusA.toString().equals(statusB.toString())) {
                return 1.0; // Identical HTTP status (e.g. 503 and 503)
            }
            if (statusA.toString().startsWith("5") && statusB.toString().startsWith("5")) {
                return 0.70; // Both 5xx server errors
            }
        }

        // Compare error message patterns
        String msgA = a.getDetails() != null ? a.getDetails().getOrDefault("message", "").toString() : "";
        String msgB = b.getDetails() != null ? b.getDetails().getOrDefault("message", "").toString() : "";

        if (!msgA.isBlank() && !msgB.isBlank() && msgA.equalsIgnoreCase(msgB)) {
            return 1.0;
        }

        return 0.0;
    }

    private double evaluateDeploymentTiming(
            AnomalyRecord a,
            AnomalyRecord b,
            List<DeploymentEvent> deployments,
            UUID tenantId) {

        if (deployments == null || deployments.isEmpty()) {
            return 0.0;
        }

        Instant earliestAnomaly = a.getFirstDetectedAt().isBefore(b.getFirstDetectedAt())
                ? a.getFirstDetectedAt() : b.getFirstDetectedAt();

        double maxScore = 0.0;

        for (DeploymentEvent d : deployments) {
            if (!tenantId.equals(d.tenantId())) continue;

            // Deployment must precede or be near anomaly onset (up to 30 min before, or 2 min after for lag)
            Duration timeDiff = Duration.between(d.deployedAt(), earliestAnomaly);
            long minutesBefore = timeDiff.toMinutes();

            if (minutesBefore >= -2 && minutesBefore <= 30) {
                boolean onDirectService = d.serviceId().equalsIgnoreCase(a.getServiceId())
                        || d.serviceId().equalsIgnoreCase(b.getServiceId());

                if (onDirectService) {
                    double score = minutesBefore <= 15 ? 1.0 : 0.60;
                    maxScore = Math.max(maxScore, score);
                } else {
                    // Check if on a neighbor in the topology
                    int distA = graphService.getShortestDistance(tenantId, d.serviceId(), a.getServiceId());
                    int distB = graphService.getShortestDistance(tenantId, d.serviceId(), b.getServiceId());
                    if (distA == 1 || distB == 1) {
                        double score = minutesBefore <= 15 ? 0.80 : 0.40;
                        maxScore = Math.max(maxScore, score);
                    }
                }
            }
        }

        return maxScore;
    }

    private double evaluateInfrastructure(
            String serviceA,
            String serviceB,
            Map<String, ServiceInfrastructureMetadata> infraMetadata) {

        if (infraMetadata == null || infraMetadata.isEmpty()) {
            return 0.0;
        }

        ServiceInfrastructureMetadata metaA = infraMetadata.get(serviceA);
        ServiceInfrastructureMetadata metaB = infraMetadata.get(serviceB);

        if (metaA == null || metaB == null) {
            return 0.0;
        }

        // Shared database instance
        if (metaA.databaseInstance() != null && metaA.databaseInstance().equalsIgnoreCase(metaB.databaseInstance())) {
            return 1.0;
        }

        // Shared physical host
        if (metaA.host() != null && metaA.host().equalsIgnoreCase(metaB.host())) {
            return 1.0;
        }

        // Shared cluster / namespace
        if (metaA.cluster() != null && metaA.cluster().equalsIgnoreCase(metaB.cluster())) {
            return 0.60;
        }

        return 0.0;
    }

    private double evaluateConfigChanges(
            AnomalyRecord a,
            AnomalyRecord b,
            List<ConfigChangeEvent> configChanges,
            UUID tenantId) {

        if (configChanges == null || configChanges.isEmpty()) {
            return 0.0;
        }

        Instant earliestAnomaly = a.getFirstDetectedAt().isBefore(b.getFirstDetectedAt())
                ? a.getFirstDetectedAt() : b.getFirstDetectedAt();

        double maxScore = 0.0;

        for (ConfigChangeEvent c : configChanges) {
            if (!tenantId.equals(c.tenantId())) continue;

            long minutesBefore = Duration.between(c.changedAt(), earliestAnomaly).toMinutes();
            if (minutesBefore >= -2 && minutesBefore <= 30) {
                boolean onDirectService = c.serviceId().equalsIgnoreCase(a.getServiceId())
                        || c.serviceId().equalsIgnoreCase(b.getServiceId());

                if (onDirectService) {
                    maxScore = Math.max(maxScore, 1.0);
                } else {
                    int distA = graphService.getShortestDistance(tenantId, c.serviceId(), a.getServiceId());
                    int distB = graphService.getShortestDistance(tenantId, c.serviceId(), b.getServiceId());
                    if (distA == 1 || distB == 1) {
                        maxScore = Math.max(maxScore, 0.70);
                    }
                }
            }
        }

        return maxScore;
    }

    private double evaluatePriorHistory(String serviceA, String serviceB, Set<String> coFailureHistory) {
        if (coFailureHistory == null || coFailureHistory.isEmpty()) {
            return 0.0;
        }

        String key1 = serviceA.toLowerCase() + ":" + serviceB.toLowerCase();
        String key2 = serviceB.toLowerCase() + ":" + serviceA.toLowerCase();

        if (coFailureHistory.contains(key1) || coFailureHistory.contains(key2)) {
            return 1.0;
        }

        return 0.0;
    }
}
