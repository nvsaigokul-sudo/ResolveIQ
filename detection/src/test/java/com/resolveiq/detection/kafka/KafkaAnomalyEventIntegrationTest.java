package com.resolveiq.detection.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.resolveiq.common.telemetry.AnomalyDetectedPayload;
import com.resolveiq.common.telemetry.AnomalyLifecycleState;
import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.fingerprint.AnomalyFingerprinter;
import com.resolveiq.detection.lifecycle.AnomalyLifecycleManager;
import com.resolveiq.detection.model.AnomalyRecord;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.statistical.HysteresisEvaluator;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kafka Anomaly Event Integration Tests conforming to PRD §18, §51.
 * Verifies Kafka envelope schema, partition key (tenant_id:service_id),
 * and event emission for both RAISED and RESOLVED lifecycle transitions.
 */
class KafkaAnomalyEventIntegrationTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private AnomalyEventProducer eventProducer;
    private AnomalyLifecycleManager lifecycleManager;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> mockTemplate = Mockito.mock(KafkaTemplate.class);
        this.kafkaTemplate = mockTemplate;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // Default successful Kafka send response
        CompletableFuture<SendResult<String, String>> successFuture = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(successFuture);

        this.eventProducer = new AnomalyEventProducer(kafkaTemplate, objectMapper, "telemetry.anomalies");
        this.lifecycleManager = new AnomalyLifecycleManager(new AnomalyFingerprinter(), new HysteresisEvaluator(), 15);
    }

    @Test
    @DisplayName("Kafka publication: Emits AnomalyDetected event with partition key tenant_id:service_id and valid envelope")
    void testAnomalyEventKafkaPublication() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String serviceId = "payment-service";
        String metricName = "error_rate_5xx";

        AnomalyRecord record = new AnomalyRecord(
                UUID.randomUUID(),
                "f9b7c8e90a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d",
                tenantId,
                projectId,
                serviceId,
                metricName,
                "production",
                "rule.error_rate_5xx",
                DetectorType.RULE_BASED,
                Severity.CRITICAL,
                AnomalyLifecycleState.RAISED,
                14.2,
                5.0,
                3.8,
                Instant.now(),
                Map.of("cluster", "prod-us-east-1", "host", "k8s-worker-42")
        );

        eventProducer.publishAnomalyEvent(record);

        // Capture Kafka ProducerRecord
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, String> recordSent = captor.getValue();
        assertThat(recordSent.topic()).isEqualTo("telemetry.anomalies");

        // Mandatory PRD Section 51 partition key requirement: tenant_id:service_id
        String expectedKey = tenantId + ":" + serviceId;
        assertThat(recordSent.key()).isEqualTo(expectedKey);

        // Deserialize and verify PRD Section 51 envelope structure
        String jsonPayload = recordSent.value();
        assertThat(jsonPayload).isNotNull().isNotBlank();

        KafkaEventEnvelope<AnomalyDetectedPayload> envelope = objectMapper.readValue(
                jsonPayload, new TypeReference<>() {});

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.tenantId()).isEqualTo(tenantId);
        assertThat(envelope.projectId()).isEqualTo(projectId);
        assertThat(envelope.environment()).isEqualTo("production");
        assertThat(envelope.schemaVersion()).isEqualTo("1.0");
        assertThat(envelope.producer()).isEqualTo("detection-engine");
        assertThat(envelope.timestamp()).isNotNull();

        // Verify AnomalyDetected payload contents
        AnomalyDetectedPayload payload = envelope.payload();
        assertThat(payload.anomalyId()).isEqualTo(record.getAnomalyId());
        assertThat(payload.fingerprint()).isEqualTo(record.getFingerprint());
        assertThat(payload.detectorId()).isEqualTo("rule.error_rate_5xx");
        assertThat(payload.detectorType()).isEqualTo(DetectorType.RULE_BASED);
        assertThat(payload.serviceId()).isEqualTo("payment-service");
        assertThat(payload.metricName()).isEqualTo("error_rate_5xx");
        assertThat(payload.currentValue()).isEqualTo(14.2);
        assertThat(payload.thresholdValue()).isEqualTo(5.0);
        assertThat(payload.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(payload.lifecycleState()).isEqualTo(AnomalyLifecycleState.RAISED);
        assertThat(payload.deviationSigma()).isEqualTo(3.8);
        assertThat(payload.details()).containsEntry("cluster", "prod-us-east-1");

        // Verify in-memory verification queue
        assertThat(eventProducer.getEmittedEvents()).hasSize(1);
    }

    @Test
    @DisplayName("Kafka publication: Resolving an anomaly publishes RESOLVED lifecycle event")
    void testResolvedAnomalyEventPublication() {
        UUID tenantId = UUID.randomUUID();
        String serviceId = "checkout-service";
        String metricName = "latency_p99";

        // 1. Initial breach -> RAISED
        DetectionEvaluationResult breach = DetectionEvaluationResult.breach(
                "rule.latency_threshold", DetectorType.RULE_BASED, 950.0, 500.0,
                Severity.CRITICAL, 10, null, "p99 latency breach", Map.of()
        );
        Optional<AnomalyRecord> raisedOpt = lifecycleManager.processEvaluation(
                tenantId, null, serviceId, metricName, "production", breach);
        assertThat(raisedOpt).isPresent();
        eventProducer.publishAnomalyEvent(raisedOpt.get());

        // 2. Value returns below recovery threshold (500 * 0.85 = 425) for 2 cycles -> RESOLVED
        DetectionEvaluationResult normal1 = DetectionEvaluationResult.ok(
                "rule.latency_threshold", DetectorType.RULE_BASED, 200.0, 500.0, 10);
        lifecycleManager.processEvaluation(tenantId, null, serviceId, metricName, "production", normal1);

        DetectionEvaluationResult normal2 = DetectionEvaluationResult.ok(
                "rule.latency_threshold", DetectorType.RULE_BASED, 180.0, 500.0, 10);
        Optional<AnomalyRecord> resolvedOpt = lifecycleManager.processEvaluation(
                tenantId, null, serviceId, metricName, "production", normal2);

        assertThat(resolvedOpt).isPresent();
        assertThat(resolvedOpt.get().getLifecycleState()).isEqualTo(AnomalyLifecycleState.RESOLVED);

        eventProducer.publishAnomalyEvent(resolvedOpt.get());

        // Verify 2 events were emitted: 1 RAISED, 1 RESOLVED
        assertThat(eventProducer.getEmittedEvents()).hasSize(2);
        var eventsList = eventProducer.getEmittedEvents().stream().toList();
        assertThat(eventsList.get(0).payload().lifecycleState()).isEqualTo(AnomalyLifecycleState.RAISED);
        assertThat(eventsList.get(1).payload().lifecycleState()).isEqualTo(AnomalyLifecycleState.RESOLVED);
        assertThat(eventsList.get(1).payload().serviceId()).isEqualTo(serviceId);
    }
}
