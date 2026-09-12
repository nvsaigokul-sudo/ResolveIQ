package com.resolveiq.correlation.engine;

import com.resolveiq.common.correlation.BlastRadiusResult;
import com.resolveiq.common.correlation.CandidateOriginScore;
import com.resolveiq.common.correlation.ConfidenceTier;
import com.resolveiq.common.correlation.CorrelationSignalBreakdown;
import com.resolveiq.correlation.fingerprint.IncidentFingerprinter;
import com.resolveiq.correlation.graph.DependencyGraphService;
import com.resolveiq.correlation.kafka.IncidentEventProducer;
import com.resolveiq.correlation.model.ConfigChangeEvent;
import com.resolveiq.correlation.model.CorrelatedIncidentGroup;
import com.resolveiq.correlation.model.DeploymentEvent;
import com.resolveiq.correlation.model.ServiceInfrastructureMetadata;
import com.resolveiq.detection.model.AnomalyRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic Multi-Signal Correlation Orchestration Engine (PRD §19, §20, §51).
 * Correlates anomalies across 8 weighted signals, builds incident groups,
 * computes blast radius, identifies root cause candidates, and emits incident events with ZERO LLM dependency.
 */
@Service
public class CorrelationEngineService {

    private static final Logger log = LoggerFactory.getLogger(CorrelationEngineService.class);

    private final DependencyGraphService graphService;
    private final CorrelationSignalEvaluator signalEvaluator;
    private final IncidentFingerprinter fingerprinter;
    private final IncidentEventProducer eventProducer;

    private final Duration correlationWindow;
    private final Duration cooldownWindow;

    // Multi-tenant active incident registry: tenantId -> Map<incidentId, CorrelatedIncidentGroup>
    private final Map<UUID, Map<UUID, CorrelatedIncidentGroup>> activeIncidents = new ConcurrentHashMap<>();

    // Multi-tenant incident fingerprint cache for deduplication: tenantId -> Map<fingerprint, Instant>
    private final Map<UUID, Map<String, Instant>> fingerprintCooldowns = new ConcurrentHashMap<>();

    // Metadata registries per tenant
    private final Map<UUID, List<DeploymentEvent>> deploymentRegistry = new ConcurrentHashMap<>();
    private final Map<UUID, List<ConfigChangeEvent>> configChangeRegistry = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ServiceInfrastructureMetadata>> infraRegistry = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> coFailureHistoryRegistry = new ConcurrentHashMap<>();

    public CorrelationEngineService(
            DependencyGraphService graphService,
            CorrelationSignalEvaluator signalEvaluator,
            IncidentFingerprinter fingerprinter,
            IncidentEventProducer eventProducer,
            @Value("${resolveiq.correlation.window-minutes:15}") int windowMinutes,
            @Value("${resolveiq.correlation.cooldown-minutes:30}") int cooldownMinutes) {
        this.graphService = graphService;
        this.signalEvaluator = signalEvaluator;
        this.fingerprinter = fingerprinter;
        this.eventProducer = eventProducer;
        this.correlationWindow = Duration.ofMinutes(windowMinutes);
        this.cooldownWindow = Duration.ofMinutes(cooldownMinutes);
    }

    // ========================================================================
    // Metadata Registration
    // ========================================================================

    public void registerDeployment(DeploymentEvent event) {
        if (event == null || event.tenantId() == null) return;
        deploymentRegistry.computeIfAbsent(event.tenantId(), k -> Collections.synchronizedList(new ArrayList<>())).add(event);
        log.info("Registered deployment for tenant {} service {} v{}", event.tenantId(), event.serviceId(), event.version());
    }

    public void registerConfigChange(ConfigChangeEvent event) {
        if (event == null || event.tenantId() == null) return;
        configChangeRegistry.computeIfAbsent(event.tenantId(), k -> Collections.synchronizedList(new ArrayList<>())).add(event);
        log.info("Registered config change for tenant {} service {} key {}", event.tenantId(), event.serviceId(), event.changeKey());
    }

    public void registerInfraMetadata(ServiceInfrastructureMetadata meta) {
        if (meta == null || meta.tenantId() == null || meta.serviceId() == null) return;
        infraRegistry.computeIfAbsent(meta.tenantId(), k -> new ConcurrentHashMap<>()).put(meta.serviceId(), meta);
    }

    public void recordHistoricalCoFailure(UUID tenantId, String serviceA, String serviceB) {
        if (tenantId == null || serviceA == null || serviceB == null) return;
        String pair = serviceA.toLowerCase() + ":" + serviceB.toLowerCase();
        coFailureHistoryRegistry.computeIfAbsent(tenantId, k -> Collections.synchronizedSet(new HashSet<>())).add(pair);
    }

    // ========================================================================
    // Anomaly Correlation & Incident Grouping
    // ========================================================================

    /**
     * Ingests an AnomalyRecord and correlates it against existing incidents or creates a new incident.
     *
     * @param anomaly anomaly to correlate
     * @return resulting CorrelatedIncidentGroup (or empty if deduplicated)
     */
    public synchronized Optional<CorrelatedIncidentGroup> correlateAnomaly(AnomalyRecord anomaly) {
        Objects.requireNonNull(anomaly, "anomaly cannot be null");
        UUID tenantId = anomaly.getTenantId();
        Instant now = Instant.now();

        Map<UUID, CorrelatedIncidentGroup> tenantIncidents = activeIncidents.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
        Map<String, Instant> cooldowns = fingerprintCooldowns.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());

        // Prune expired incidents outside correlation window
        pruneExpiredIncidents(tenantIncidents, now);

        // Retrieve metadata for this tenant
        List<DeploymentEvent> deployments = deploymentRegistry.getOrDefault(tenantId, Collections.emptyList());
        List<ConfigChangeEvent> configChanges = configChangeRegistry.getOrDefault(tenantId, Collections.emptyList());
        Map<String, ServiceInfrastructureMetadata> infra = infraRegistry.getOrDefault(tenantId, Collections.emptyMap());
        Set<String> history = coFailureHistoryRegistry.getOrDefault(tenantId, Collections.emptySet());

        // Find candidate active incident to correlate with
        CorrelatedIncidentGroup bestMatchGroup = null;
        CorrelationSignalBreakdown bestBreakdown = null;
        double highestScore = -1.0;

        for (CorrelatedIncidentGroup group : tenantIncidents.values()) {
            // Compare against primary and all correlated anomalies in the group
            for (AnomalyRecord existing : group.getCorrelatedAnomalies()) {
                CorrelationSignalBreakdown breakdown = signalEvaluator.evaluate(
                        existing, anomaly, deployments, configChanges, infra, history);

                if (breakdown.compositeScore() > highestScore) {
                    highestScore = breakdown.compositeScore();
                    bestBreakdown = breakdown;
                    bestMatchGroup = group;
                }
            }
        }

        // Check confidence threshold: POSSIBLY_RELATED or CONFIRMED_RELATIONSHIP (>= 0.45)
        if (bestMatchGroup != null && highestScore >= 0.45) {
            // Check if this anomaly is already part of the incident (replay / duplicate)
            boolean alreadyPresent = bestMatchGroup.getCorrelatedAnomalies().stream()
                    .anyMatch(existing -> existing.getAnomalyId().equals(anomaly.getAnomalyId())
                            || existing.getFingerprint().equals(anomaly.getFingerprint()));
            if (alreadyPresent) {
                log.info("Anomaly {} already present in incident {}; deduplicated replay",
                        anomaly.getAnomalyId(), bestMatchGroup.getIncidentId());
                return Optional.empty();
            }

            log.info("Correlated anomaly {} with existing incident {} (Score: {}, Tier: {})",
                    anomaly.getAnomalyId(), bestMatchGroup.getIncidentId(), highestScore, bestBreakdown.confidenceTier());

            // Build aggregated anomaly list to re-score candidate origins
            List<AnomalyRecord> updatedAnomalies = new ArrayList<>(bestMatchGroup.getCorrelatedAnomalies());
            updatedAnomalies.add(anomaly);

            List<CandidateOriginScore> candidateOrigins = graphService.scoreOriginCandidates(tenantId, updatedAnomalies);
            String newRootService = !candidateOrigins.isEmpty()
                    ? candidateOrigins.get(0).serviceId()
                    : bestMatchGroup.getRootServiceCandidate();

            BlastRadiusResult blastRadius = graphService.calculateBlastRadius(tenantId, newRootService);

            bestMatchGroup.addCorrelatedAnomaly(
                    anomaly, bestBreakdown, newRootService, blastRadius, candidateOrigins);

            eventProducer.publishIncidentEvent(bestMatchGroup);
            return Optional.of(bestMatchGroup);

        } else {
            // Uncorrelated / new incident
            String errorSignature = extractErrorSignature(anomaly);
            String fingerprint = fingerprinter.computeFingerprint(
                    tenantId, anomaly.getServiceId(), anomaly.getDetectorType().name(), errorSignature);

            // Check deduplication cooldown
            Instant lastEmitted = cooldowns.get(fingerprint);
            if (lastEmitted != null && Duration.between(lastEmitted, now).compareTo(cooldownWindow) < 0) {
                log.info("Deduplicated incident with fingerprint {} within cooldown ({}m)",
                        fingerprint, cooldownWindow.toMinutes());
                return Optional.empty();
            }

            // Standalone correlation breakdown (self-score 1.0)
            CorrelationSignalBreakdown standaloneBreakdown = CorrelationSignalBreakdown.compute(
                    1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0);

            BlastRadiusResult blastRadius = graphService.calculateBlastRadius(tenantId, anomaly.getServiceId());
            List<CandidateOriginScore> candidateOrigins = graphService.scoreOriginCandidates(tenantId, List.of(anomaly));

            UUID incidentId = UUID.randomUUID();
            CorrelatedIncidentGroup newGroup = new CorrelatedIncidentGroup(
                    incidentId,
                    tenantId,
                    anomaly.getProjectId(),
                    anomaly.getEnvironment(),
                    fingerprint,
                    anomaly.getServiceId(),
                    anomaly,
                    anomaly.getSeverity(),
                    ConfidenceTier.CONFIRMED_RELATIONSHIP,
                    1.0,
                    standaloneBreakdown,
                    blastRadius,
                    candidateOrigins,
                    now
            );

            tenantIncidents.put(incidentId, newGroup);
            cooldowns.put(fingerprint, now);

            log.warn("Created NEW incident {} (Fingerprint: {}, Root: {}, Blast: {} services)",
                    incidentId, fingerprint, anomaly.getServiceId(), blastRadius.totalDownstreamCount());

            eventProducer.publishIncidentEvent(newGroup);
            return Optional.of(newGroup);
        }
    }

    private String extractErrorSignature(AnomalyRecord anomaly) {
        if (anomaly.getDetails() != null && anomaly.getDetails().containsKey("status")) {
            return "status_" + anomaly.getDetails().get("status");
        }
        if (anomaly.getDetails() != null && anomaly.getDetails().containsKey("message")) {
            return anomaly.getDetails().get("message").toString();
        }
        return anomaly.getDetectorId();
    }

    private void pruneExpiredIncidents(Map<UUID, CorrelatedIncidentGroup> incidents, Instant now) {
        Instant cutoff = now.minus(correlationWindow);
        incidents.entrySet().removeIf(entry -> entry.getValue().getUpdatedAt().isBefore(cutoff));
    }

    public Map<UUID, CorrelatedIncidentGroup> getActiveIncidents(UUID tenantId) {
        Map<UUID, CorrelatedIncidentGroup> map = activeIncidents.get(tenantId);
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }

    public void clear() {
        activeIncidents.clear();
        fingerprintCooldowns.clear();
        deploymentRegistry.clear();
        configChangeRegistry.clear();
        infraRegistry.clear();
        coFailureHistoryRegistry.clear();
        eventProducer.clear();
    }
}
