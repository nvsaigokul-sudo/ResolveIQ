package com.resolveiq.correlation.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.correlation.ConfidenceTier;
import com.resolveiq.common.correlation.CorrelationSignalBreakdown;
import com.resolveiq.common.telemetry.AnomalyLifecycleState;
import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.correlation.fingerprint.IncidentFingerprinter;
import com.resolveiq.correlation.graph.DependencyGraphService;
import com.resolveiq.correlation.kafka.IncidentEventProducer;
import com.resolveiq.correlation.model.ConfigChangeEvent;
import com.resolveiq.correlation.model.CorrelatedIncidentGroup;
import com.resolveiq.correlation.model.DeploymentEvent;
import com.resolveiq.correlation.model.ServiceInfrastructureMetadata;
import com.resolveiq.detection.model.AnomalyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic Multi-Signal Correlation Engine Tests (PRD §19, §20, §51).
 * Verifies all 8 signals, exact weights, confidence tiers, negative/adversarial isolation,
 * simultaneous anomaly grouping, missing trace handling, and replay idempotency with ZERO LLM dependency.
 */
class MultiSignalCorrelationEngineTest {

    private DependencyGraphService graphService;
    private CorrelationSignalEvaluator signalEvaluator;
    private IncidentFingerprinter fingerprinter;
    private IncidentEventProducer eventProducer;
    private CorrelationEngineService correlationEngineService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        graphService = new DependencyGraphService();
        signalEvaluator = new CorrelationSignalEvaluator(graphService);
        fingerprinter = new IncidentFingerprinter();

        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        eventProducer = new IncidentEventProducer(kafkaTemplate, new ObjectMapper(), "incidents");

        correlationEngineService = new CorrelationEngineService(
                graphService, signalEvaluator, fingerprinter, eventProducer, 15, 30);
        tenantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Signal Weights: Confirms all 8 PRD weights sum exactly to 1.00 and produce bounded scores")
    void testSignalWeightsAndCompositeScoreCalculation() {
        // Assert weights sum to 1.00
        double weightSum = CorrelationSignalBreakdown.WEIGHT_TIME_PROXIMITY
                + CorrelationSignalBreakdown.WEIGHT_TOPOLOGY_DISTANCE
                + CorrelationSignalBreakdown.WEIGHT_TRACE_LINKAGE
                + CorrelationSignalBreakdown.WEIGHT_SHARED_ERROR_SIGNATURE
                + CorrelationSignalBreakdown.WEIGHT_DEPLOYMENT_TIMING
                + CorrelationSignalBreakdown.WEIGHT_COMMON_INFRASTRUCTURE
                + CorrelationSignalBreakdown.WEIGHT_CONFIG_CHANGES
                + CorrelationSignalBreakdown.WEIGHT_PRIOR_INCIDENT_HISTORY;

        assertThat(weightSum).isEqualTo(1.00);

        // Perfect scores across all signals -> 1.00
        CorrelationSignalBreakdown perfect = CorrelationSignalBreakdown.compute(
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        assertThat(perfect.compositeScore()).isEqualTo(1.00);
        assertThat(perfect.confidenceTier()).isEqualTo(ConfidenceTier.CONFIRMED_RELATIONSHIP);

        // Zero scores -> 0.00
        CorrelationSignalBreakdown zero = CorrelationSignalBreakdown.compute(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        assertThat(zero.compositeScore()).isEqualTo(0.00);
        assertThat(zero.confidenceTier()).isEqualTo(ConfidenceTier.WEAK_RELATIONSHIP);
    }

    @Test
    @DisplayName("Confidence Tiers: Accurately maps composite scores to CONFIRMED, POSSIBLY, and WEAK")
    void testConfidenceTierBoundaries() {
        // CONFIRMED_RELATIONSHIP >= 0.75
        assertThat(ConfidenceTier.fromScore(0.75)).isEqualTo(ConfidenceTier.CONFIRMED_RELATIONSHIP);
        assertThat(ConfidenceTier.fromScore(0.85)).isEqualTo(ConfidenceTier.CONFIRMED_RELATIONSHIP);

        // POSSIBLY_RELATED: 0.45 <= score < 0.75
        assertThat(ConfidenceTier.fromScore(0.45)).isEqualTo(ConfidenceTier.POSSIBLY_RELATED);
        assertThat(ConfidenceTier.fromScore(0.74)).isEqualTo(ConfidenceTier.POSSIBLY_RELATED);

        // WEAK_RELATIONSHIP < 0.45
        assertThat(ConfidenceTier.fromScore(0.44)).isEqualTo(ConfidenceTier.WEAK_RELATIONSHIP);
        assertThat(ConfidenceTier.fromScore(0.10)).isEqualTo(ConfidenceTier.WEAK_RELATIONSHIP);
    }

    @Test
    @DisplayName("Correlation Match: Correlates dependent order and payment anomalies into a single incident")
    void testStronglyCorrelatedAnomaliesMergedIntoSingleIncident() {
        Instant t0 = Instant.parse("2026-09-12T11:00:00Z");

        // 1. Dependency Graph: order-service -> payment-service
        graphService.recordServiceCall(tenantId, "order-service", "payment-service", false, 50.0, t0);

        // 2. Metadata: Recent deployment on payment-service v2.8 at t0 - 5 min
        correlationEngineService.registerDeployment(new DeploymentEvent(
                tenantId, "payment-service", "v2.8", t0.minusSeconds(300), "production", "a1b2c3d"));

        // 3. Metadata: Shared cluster and database
        correlationEngineService.registerInfraMetadata(new ServiceInfrastructureMetadata(
                tenantId, "payment-service", "prod-cluster", "k8s-node-1", "us-east-1a", "postgres-primary"));
        correlationEngineService.registerInfraMetadata(new ServiceInfrastructureMetadata(
                tenantId, "order-service", "prod-cluster", "k8s-node-2", "us-east-1a", "postgres-primary"));

        // Anomaly 1 on payment-service (Hikari connection timeout)
        AnomalyRecord aPayment = new AnomalyRecord(
                UUID.randomUUID(), "fp-pay", tenantId, null, "payment-service", "db_connections",
                "production", "rule.resource_utilization", DetectorType.RULE_BASED, Severity.CRITICAL,
                AnomalyLifecycleState.RAISED, 98.0, 85.0, null, t0,
                Map.of("message", "HikariPool-1 connection timeout", "status", "500", "trace_id", "trace-pay-999")
        );

        // Anomaly 2 on order-service 30 seconds later (503 downstream failure, sharing trace-pay-999)
        AnomalyRecord aOrder = new AnomalyRecord(
                UUID.randomUUID(), "fp-ord", tenantId, null, "order-service", "error_rate",
                "production", "rule.error_rate_5xx", DetectorType.RULE_BASED, Severity.HIGH,
                AnomalyLifecycleState.RAISED, 14.0, 5.0, null, t0.plusSeconds(30),
                Map.of("message", "503 Service Unavailable", "status", "503", "trace_id", "trace-pay-999")
        );

        // Ingest Anomaly 1 -> creates new incident
        Optional<CorrelatedIncidentGroup> inc1 = correlationEngineService.correlateAnomaly(aPayment);
        assertThat(inc1).isPresent();
        UUID incidentId = inc1.get().getIncidentId();

        // Ingest Anomaly 2 -> correlates into existing incident!
        Optional<CorrelatedIncidentGroup> inc2 = correlationEngineService.correlateAnomaly(aOrder);
        assertThat(inc2).isPresent();

        CorrelatedIncidentGroup merged = inc2.get();
        assertThat(merged.getIncidentId()).isEqualTo(incidentId);
        assertThat(merged.getCorrelatedAnomalies()).hasSize(2);
        assertThat(merged.getRootServiceCandidate()).isEqualTo("payment-service");
        assertThat(merged.getConfidenceTier()).isEqualTo(ConfidenceTier.CONFIRMED_RELATIONSHIP);
        assertThat(merged.getCompositeCorrelationScore()).isGreaterThanOrEqualTo(0.75);

        // Exactly 1 active incident exists for this tenant
        assertThat(correlationEngineService.getActiveIncidents(tenantId)).hasSize(1);
    }

    @Test
    @DisplayName("Negative Test: Unrelated orthogonal anomalies are NOT grouped together")
    void testUnrelatedAnomaliesSeparatedIntoDistinctIncidents() {
        Instant t0 = Instant.parse("2026-09-12T11:00:00Z");

        // Disconnected services in graph
        graphService.recordServiceCall(tenantId, "auth-service", "auth-db", false, 10.0, t0);
        graphService.recordServiceCall(tenantId, "analytics-service", "clickhouse", false, 50.0, t0);

        // Anomaly 1: Auth service latency spike
        AnomalyRecord aAuth = new AnomalyRecord(
                UUID.randomUUID(), "fp-auth", tenantId, null, "auth-service", "latency_p95",
                "production", "rule.latency_threshold", DetectorType.RULE_BASED, Severity.HIGH,
                AnomalyLifecycleState.RAISED, 600.0, 200.0, null, t0,
                Map.of("message", "Auth latency breach", "status", "200")
        );

        // Anomaly 2: Analytics service queue depth surge 8 minutes later (disconnected, orthogonal)
        AnomalyRecord aAnalytics = new AnomalyRecord(
                UUID.randomUUID(), "fp-analytics", tenantId, null, "analytics-service", "queue_depth",
                "production", "rule.queue_depth", DetectorType.RULE_BASED, Severity.HIGH,
                AnomalyLifecycleState.RAISED, 4500.0, 1000.0, null, t0.plusSeconds(480),
                Map.of("message", "Kafka ingestion lag", "status", "N/A")
        );

        Optional<CorrelatedIncidentGroup> inc1 = correlationEngineService.correlateAnomaly(aAuth);
        Optional<CorrelatedIncidentGroup> inc2 = correlationEngineService.correlateAnomaly(aAnalytics);

        assertThat(inc1).isPresent();
        assertThat(inc2).isPresent();

        // Must produce two completely separate incident groups!
        assertThat(inc1.get().getIncidentId()).isNotEqualTo(inc2.get().getIncidentId());
        assertThat(correlationEngineService.getActiveIncidents(tenantId)).hasSize(2);
    }

    @Test
    @DisplayName("Simultaneous Multi-Anomalies: Groups related pair and isolates unrelated anomaly occurring at the same second")
    void testSimultaneousMultiAnomalyPartitioning() {
        Instant now = Instant.now();

        // Topology: frontend -> checkout
        graphService.recordServiceCall(tenantId, "frontend", "checkout", false, 30.0, now);
        // Isolated service: email-worker
        graphService.recordServiceCall(tenantId, "email-worker", "smtp-relay", false, 20.0, now);

        // Ingest deployment on checkout
        correlationEngineService.registerDeployment(new DeploymentEvent(
                tenantId, "checkout", "v1.4", now.minusSeconds(120), "production", "commit-1"));

        // 3 simultaneous anomalies at the exact same timestamp:
        AnomalyRecord aCheckout = new AnomalyRecord(
                UUID.randomUUID(), "fp-chk", tenantId, null, "checkout", "error_rate",
                "production", "rule.error_rate_5xx", DetectorType.RULE_BASED, Severity.CRITICAL,
                AnomalyLifecycleState.RAISED, 22.0, 5.0, null, now,
                Map.of("status", "500", "trace_id", "trace-simultaneous-1")
        );

        AnomalyRecord aFrontend = new AnomalyRecord(
                UUID.randomUUID(), "fp-fe", tenantId, null, "frontend", "error_rate",
                "production", "rule.error_rate_5xx", DetectorType.RULE_BASED, Severity.HIGH,
                AnomalyLifecycleState.RAISED, 15.0, 5.0, null, now,
                Map.of("status", "502", "trace_id", "trace-simultaneous-1")
        );

        AnomalyRecord aEmail = new AnomalyRecord(
                UUID.randomUUID(), "fp-email", tenantId, null, "email-worker", "queue_depth",
                "production", "rule.queue_depth", DetectorType.RULE_BASED, Severity.HIGH,
                AnomalyLifecycleState.RAISED, 2500.0, 1000.0, null, now,
                Map.of("message", "SMTP queue backlog")
        );

        correlationEngineService.correlateAnomaly(aCheckout);
        correlationEngineService.correlateAnomaly(aFrontend);
        correlationEngineService.correlateAnomaly(aEmail);

        Map<UUID, CorrelatedIncidentGroup> incidents = correlationEngineService.getActiveIncidents(tenantId);
        // Expect exactly 2 incidents:
        // Incident 1: checkout + frontend (correlated)
        // Incident 2: email-worker (isolated)
        assertThat(incidents).hasSize(2);

        CorrelatedIncidentGroup checkoutIncident = incidents.values().stream()
                .filter(i -> i.getRootServiceCandidate().equals("checkout"))
                .findFirst().orElseThrow();
        assertThat(checkoutIncident.getCorrelatedAnomalies()).hasSize(2);

        CorrelatedIncidentGroup emailIncident = incidents.values().stream()
                .filter(i -> i.getRootServiceCandidate().equals("email-worker"))
                .findFirst().orElseThrow();
        assertThat(emailIncident.getCorrelatedAnomalies()).hasSize(1);
    }

    @Test
    @DisplayName("Incomplete Trace Data: Gracefully correlates based on time, topology, and deployment signals when traces are missing")
    void testMissingTraceLinkageGracefulDegradation() {
        Instant now = Instant.now();

        // Topology: service-a -> service-b
        graphService.recordServiceCall(tenantId, "service-a", "service-b", false, 40.0, now);

        // Recent deployment on service-b
        correlationEngineService.registerDeployment(new DeploymentEvent(
                tenantId, "service-b", "v3.0", now.minusSeconds(200), "production", "c-30"));

        // Anomaly A has NO trace context
        AnomalyRecord a1 = new AnomalyRecord(
                UUID.randomUUID(), "fp-a1", tenantId, null, "service-b", "error_rate",
                "production", "rule.error_rate_5xx", DetectorType.RULE_BASED, Severity.CRITICAL,
                AnomalyLifecycleState.RAISED, 15.0, 5.0, null, now,
                Map.of("status", "500") // No trace_id!
        );

        // Anomaly B has NO trace context
        AnomalyRecord a2 = new AnomalyRecord(
                UUID.randomUUID(), "fp-a2", tenantId, null, "service-a", "error_rate",
                "production", "rule.error_rate_5xx", DetectorType.RULE_BASED, Severity.HIGH,
                AnomalyLifecycleState.RAISED, 12.0, 5.0, null, now.plusSeconds(10),
                Map.of("status", "500") // No trace_id!
        );

        correlationEngineService.correlateAnomaly(a1);
        Optional<CorrelatedIncidentGroup> correlated = correlationEngineService.correlateAnomaly(a2);

        // Time proximity (0.20 * 0.96) + Topology (0.25 * 0.90) + Error (0.10 * 1.0) + Deploy (0.10 * 1.0) = ~0.61 -> POSSIBLY_RELATED
        assertThat(correlated).isPresent();
        assertThat(correlated.get().getCorrelatedAnomalies()).hasSize(2);
        assertThat(correlated.get().getConfidenceTier()).isEqualTo(ConfidenceTier.POSSIBLY_RELATED);
    }

    @Test
    @DisplayName("Replay and Idempotency: Processing duplicate anomaly produces identical fingerprint and does not create duplicate incidents")
    void testReplayIdempotency() {
        Instant now = Instant.now();

        AnomalyRecord a = new AnomalyRecord(
                UUID.randomUUID(), "fp-idempotent", tenantId, null, "user-service", "error_rate",
                "production", "rule.error_rate_5xx", DetectorType.RULE_BASED, Severity.HIGH,
                AnomalyLifecycleState.RAISED, 10.0, 5.0, null, now,
                Map.of("status", "500")
        );

        // First pass -> Incident created
        Optional<CorrelatedIncidentGroup> inc1 = correlationEngineService.correlateAnomaly(a);
        assertThat(inc1).isPresent();
        String originalFingerprint = inc1.get().getIncidentFingerprint();

        // Replay same anomaly -> Deduplicated within cooldown window (returns empty, creates no duplicates)
        Optional<CorrelatedIncidentGroup> replay = correlationEngineService.correlateAnomaly(a);
        assertThat(replay).isEmpty();

        // Active incidents remains 1
        assertThat(correlationEngineService.getActiveIncidents(tenantId)).hasSize(1);
        assertThat(correlationEngineService.getActiveIncidents(tenantId).values().iterator().next().getIncidentFingerprint())
                .isEqualTo(originalFingerprint);
    }
}
