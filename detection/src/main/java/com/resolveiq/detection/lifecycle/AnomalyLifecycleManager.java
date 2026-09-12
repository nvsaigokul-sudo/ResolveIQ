package com.resolveiq.detection.lifecycle;

import com.resolveiq.common.telemetry.AnomalyLifecycleState;
import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.fingerprint.AnomalyFingerprinter;
import com.resolveiq.detection.model.AnomalyRecord;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.statistical.HysteresisEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anomaly Lifecycle State Machine Manager (PRD §16, §17, §18).
 * Enforces strictly valid state transitions:
 * OBSERVED -> EVALUATED -> RAISED -> DEDUPLICATED -> SUPPRESSED -> RESOLVED.
 * Integrates cooldown suppression, maintenance windows, and hysteresis recovery.
 */
@Component
public class AnomalyLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(AnomalyLifecycleManager.class);

    private final AnomalyFingerprinter fingerprinter;
    private final HysteresisEvaluator hysteresisEvaluator;
    private final Duration cooldownDuration;

    // Active anomalies keyed by fingerprint
    private final Map<String, AnomalyRecord> activeAnomalies = new ConcurrentHashMap<>();

    // Maintenance windows: "tenantId:serviceId" -> expiry Instant
    private final Map<String, Instant> maintenanceWindows = new ConcurrentHashMap<>();

    // State transition audit log: fingerprint -> list of transition descriptions
    private final Map<String, List<String>> transitionAuditLogs = new ConcurrentHashMap<>();

    public AnomalyLifecycleManager(
            AnomalyFingerprinter fingerprinter,
            HysteresisEvaluator hysteresisEvaluator,
            @Value("${resolveiq.detection.cooldown-minutes:15}") int cooldownMinutes) {
        this.fingerprinter = fingerprinter;
        this.hysteresisEvaluator = hysteresisEvaluator;
        this.cooldownDuration = Duration.ofMinutes(cooldownMinutes);
    }

    /**
     * Schedules a maintenance window for a tenant service.
     */
    public void setMaintenanceWindow(UUID tenantId, String serviceId, Instant until) {
        String key = tenantId + ":" + serviceId;
        maintenanceWindows.put(key, until);
        log.info("Scheduled maintenance window for {} until {}", key, until);
    }

    public boolean isUnderMaintenance(UUID tenantId, String serviceId) {
        String key = tenantId + ":" + serviceId;
        Instant expiry = maintenanceWindows.get(key);
        if (expiry == null) {
            return false;
        }
        if (Instant.now().isAfter(expiry)) {
            maintenanceWindows.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Processes evaluation result through the anomaly lifecycle state machine.
     *
     * @param tenantId    tenant identifier
     * @param projectId   project identifier
     * @param serviceId   service identifier
     * @param metricName  metric evaluated
     * @param environment environment (e.g. production)
     * @param result      detector evaluation result
     * @return resulting AnomalyRecord if RAISED or transition occurred, or empty if normal/deduplicated
     */
    public Optional<AnomalyRecord> processEvaluation(
            UUID tenantId,
            UUID projectId,
            String serviceId,
            String metricName,
            String environment,
            DetectionEvaluationResult result) {

        String fingerprint = fingerprinter.computeFingerprint(
                result.detectorId(), tenantId, serviceId, metricName, environment);

        AnomalyRecord existing = activeAnomalies.get(fingerprint);

        if (result.isBreached()) {
            return handleBreachState(tenantId, projectId, serviceId, metricName, environment, fingerprint, result, existing);
        } else {
            return handleNormalState(fingerprint, result, existing);
        }
    }

    private Optional<AnomalyRecord> handleBreachState(
            UUID tenantId,
            UUID projectId,
            String serviceId,
            String metricName,
            String environment,
            String fingerprint,
            DetectionEvaluationResult result,
            AnomalyRecord existing) {

        Instant now = Instant.now();

        // Check if under maintenance window
        if (isUnderMaintenance(tenantId, serviceId)) {
            if (existing != null) {
                recordTransition(fingerprint, existing.getLifecycleState(), AnomalyLifecycleState.SUPPRESSED, "Active maintenance window");
                existing.setLifecycleState(AnomalyLifecycleState.SUPPRESSED);
            }
            log.info("Anomaly {} suppressed due to active maintenance window on service {}", fingerprint, serviceId);
            return Optional.empty();
        }

        if (existing == null) {
            // State: OBSERVED -> EVALUATED -> RAISED
            AnomalyRecord newAnomaly = new AnomalyRecord(
                    UUID.randomUUID(),
                    fingerprint,
                    tenantId,
                    projectId,
                    serviceId,
                    metricName,
                    environment,
                    result.detectorId(),
                    result.detectorType(),
                    result.severity(),
                    AnomalyLifecycleState.RAISED,
                    result.observedValue(),
                    result.thresholdValue(),
                    result.deviationSigma(),
                    now,
                    result.context()
            );

            recordTransition(fingerprint, null, AnomalyLifecycleState.OBSERVED, "Telemetry threshold breach observed");
            recordTransition(fingerprint, AnomalyLifecycleState.OBSERVED, AnomalyLifecycleState.EVALUATED, "Sample count and hysteresis verified");
            recordTransition(fingerprint, AnomalyLifecycleState.EVALUATED, AnomalyLifecycleState.RAISED, result.message());

            activeAnomalies.put(fingerprint, newAnomaly);
            log.warn("RAISED new anomaly: fingerprint={}, detector={}, service={}, value={}",
                    fingerprint, result.detectorId(), serviceId, result.observedValue());
            return Optional.of(newAnomaly);

        } else {
            // Existing active anomaly: update metrics
            existing.setLastObservedValue(result.observedValue());
            existing.setLastEvaluatedAt(now);
            existing.incrementConsecutiveBreachCount();
            existing.resetConsecutiveNormalCount();

            // Check cooldown window for re-alerting
            Duration timeSinceFirstDetection = Duration.between(existing.getFirstDetectedAt(), now);
            if (timeSinceFirstDetection.compareTo(cooldownDuration) < 0) {
                // Within cooldown window -> DEDUPLICATED
                if (existing.getLifecycleState() != AnomalyLifecycleState.DEDUPLICATED) {
                    recordTransition(fingerprint, existing.getLifecycleState(), AnomalyLifecycleState.DEDUPLICATED,
                            "Repeated breach within cooldown window (" + cooldownDuration.toMinutes() + "m)");
                    existing.setLifecycleState(AnomalyLifecycleState.DEDUPLICATED);
                }
                log.debug("DEDUPLICATED active anomaly {}: repeated breach within cooldown", fingerprint);
                return Optional.empty();
            } else {
                // Cooldown elapsed: escalate / refresh RAISED
                recordTransition(fingerprint, existing.getLifecycleState(), AnomalyLifecycleState.RAISED,
                        "Cooldown elapsed; ongoing persistent anomaly re-raised");
                existing.setLifecycleState(AnomalyLifecycleState.RAISED);
                return Optional.of(existing);
            }
        }
    }

    private Optional<AnomalyRecord> handleNormalState(
            String fingerprint,
            DetectionEvaluationResult result,
            AnomalyRecord existing) {

        if (existing == null) {
            return Optional.empty(); // System was and remains normal
        }

        Instant now = Instant.now();
        existing.incrementConsecutiveNormalCount();
        existing.setLastObservedValue(result.observedValue());
        existing.setLastEvaluatedAt(now);

        // Define recovery threshold with hysteresis: recovery = 0.85 * trigger threshold
        double recoveryThreshold = existing.getThresholdValue() * 0.85;
        int requiredNormalCycles = 2;

        boolean isStillActive = hysteresisEvaluator.evaluateActiveState(
                true,
                result.observedValue(),
                existing.getThresholdValue(),
                recoveryThreshold,
                existing.getConsecutiveNormalCount(),
                requiredNormalCycles
        );

        if (!isStillActive) {
            // State: RESOLVED
            recordTransition(fingerprint, existing.getLifecycleState(), AnomalyLifecycleState.RESOLVED,
                    String.format("Observed value %.2f dropped below recovery threshold %.2f for %d consecutive cycles",
                            result.observedValue(), recoveryThreshold, existing.getConsecutiveNormalCount()));
            existing.setLifecycleState(AnomalyLifecycleState.RESOLVED);
            existing.setResolvedAt(now);

            activeAnomalies.remove(fingerprint);
            log.info("RESOLVED anomaly: fingerprint={}, detector={}, service={}",
                    fingerprint, existing.getDetectorId(), existing.getServiceId());
            return Optional.of(existing);
        }

        return Optional.empty();
    }

    private void recordTransition(String fingerprint, AnomalyLifecycleState from, AnomalyLifecycleState to, String reason) {
        String logEntry = String.format("[%s] %s -> %s: %s",
                Instant.now(), from != null ? from.name() : "NONE", to.name(), reason);
        transitionAuditLogs.computeIfAbsent(fingerprint, k -> Collections.synchronizedList(new ArrayList<>())).add(logEntry);
    }

    public List<String> getTransitionHistory(String fingerprint) {
        List<String> list = transitionAuditLogs.get(fingerprint);
        return list != null ? new ArrayList<>(list) : Collections.emptyList();
    }

    public Map<String, AnomalyRecord> getActiveAnomalies() {
        return Collections.unmodifiableMap(activeAnomalies);
    }

    public void clear() {
        activeAnomalies.clear();
        maintenanceWindows.clear();
        transitionAuditLogs.clear();
    }
}
