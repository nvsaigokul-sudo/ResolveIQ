package com.resolveiq.detection.engine;

import com.resolveiq.common.telemetry.AnomalyLifecycleState;
import com.resolveiq.detection.Detector;
import com.resolveiq.detection.kafka.AnomalyEventProducer;
import com.resolveiq.detection.lifecycle.AnomalyLifecycleManager;
import com.resolveiq.detection.model.AnomalyRecord;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.model.MetricSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic Anomaly Detection Orchestration Engine (PRD §16, §17, §18).
 * Manages sliding evaluation windows, coordinates rule-based and statistical detectors,
 * enforces tenant/service isolation, and dispatches verified anomaly events with ZERO LLM dependency.
 */
@Service
public class DetectionEngineService {

    private static final Logger log = LoggerFactory.getLogger(DetectionEngineService.class);

    private final Map<String, Detector> detectorRegistry = new ConcurrentHashMap<>();
    private final AnomalyLifecycleManager lifecycleManager;
    private final AnomalyEventProducer eventProducer;

    // Sliding evaluation windows: "tenantId:serviceId:metricName" -> List<MetricSample>
    private final Map<String, List<MetricSample>> telemetryWindows = new ConcurrentHashMap<>();

    private static final int MAX_WINDOW_SAMPLES = 500;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(15);

    public DetectionEngineService(
            List<Detector> detectors,
            AnomalyLifecycleManager lifecycleManager,
            AnomalyEventProducer eventProducer) {
        this.lifecycleManager = lifecycleManager;
        this.eventProducer = eventProducer;
        for (Detector d : detectors) {
            detectorRegistry.put(d.getId(), d);
            log.info("Registered deterministic detector: [{}] type={}", d.getId(), d.getType());
        }
    }

    /**
     * Ingests a new metric sample into the tenant/service evaluation window and evaluates target detector.
     *
     * @param tenantId       tenant identifier (mandatory)
     * @param projectId      project identifier (optional)
     * @param serviceId      service identifier (mandatory)
     * @param metricName     metric name (mandatory)
     * @param environment    environment (e.g. production)
     * @param value          observed metric value
     * @param dimensions     structured metric dimensions
     * @param timestamp      event timestamp
     * @param detectorId     ID of the detector to evaluate
     * @param detectorParams configuration parameters for detector
     * @return evaluation result
     */
    public DetectionEvaluationResult ingestAndEvaluate(
            UUID tenantId,
            UUID projectId,
            String serviceId,
            String metricName,
            String environment,
            double value,
            Map<String, String> dimensions,
            Instant timestamp,
            String detectorId,
            Map<String, Object> detectorParams) {

        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(serviceId, "serviceId cannot be null");
        Objects.requireNonNull(metricName, "metricName cannot be null");

        Detector detector = detectorRegistry.get(detectorId);
        if (detector == null) {
            throw new IllegalArgumentException("Unknown detector: " + detectorId);
        }

        Instant eventTime = timestamp != null ? timestamp : Instant.now();
        MetricSample sample = new MetricSample(eventTime, value, dimensions);

        // Strict multi-tenant buffer key
        String windowKey = tenantId + ":" + serviceId + ":" + metricName;
        List<MetricSample> window = telemetryWindows.computeIfAbsent(windowKey, k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (window) {
            window.add(sample);
            // Prune samples older than WINDOW_DURATION from the latest watermark
            Instant maxTimestamp = window.stream()
                    .map(MetricSample::timestamp)
                    .max(Instant::compareTo)
                    .orElse(eventTime);
            Instant cutoff = maxTimestamp.minus(WINDOW_DURATION);
            window.removeIf(s -> s.timestamp().isBefore(cutoff));
            while (window.size() > MAX_WINDOW_SAMPLES) {
                window.remove(0);
            }
        }

        // Deterministic evaluation (zero LLM calls)
        List<MetricSample> snapshot;
        synchronized (window) {
            snapshot = new ArrayList<>(window);
        }

        DetectionEvaluationResult result = detector.evaluate(snapshot, detectorParams);

        // Process through Anomaly Lifecycle State Machine
        Optional<AnomalyRecord> lifecycleResult = lifecycleManager.processEvaluation(
                tenantId, projectId, serviceId, metricName, environment, result);

        lifecycleResult.ifPresent(record -> {
            if (record.getLifecycleState() == AnomalyLifecycleState.RAISED ||
                record.getLifecycleState() == AnomalyLifecycleState.RESOLVED) {
                eventProducer.publishAnomalyEvent(record);
            }
        });

        return result;
    }

    public void clearWindows() {
        telemetryWindows.clear();
        lifecycleManager.clear();
    }

    public Detector getDetector(String detectorId) {
        return detectorRegistry.get(detectorId);
    }
}
