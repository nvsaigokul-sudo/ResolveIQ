package com.resolveiq.correlation.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.resolveiq.common.correlation.CorrelatedIncidentPayload;
import com.resolveiq.common.telemetry.AnomalyLifecycleState;
import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.correlation.engine.CorrelationEngineService;
import com.resolveiq.correlation.engine.CorrelationSignalEvaluator;
import com.resolveiq.correlation.fingerprint.IncidentFingerprinter;
import com.resolveiq.correlation.graph.DependencyGraphService;
import com.resolveiq.correlation.model.CorrelatedIncidentGroup;
import com.resolveiq.correlation.model.DeploymentEvent;
import com.resolveiq.detection.model.AnomalyRecord;
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
 * Kafka Incident Event Integration Tests (PRD §19, §20, §51).
 * Verifies Kafka envelope schema, partition key (tenant_id:root_service),
 * and payload integrity for IncidentCreated and IncidentCorrelated events.
 */
class KafkaIncidentIntegrationTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private IncidentEventProducer eventProducer;
    private DependencyGraphService graphService;
    private CorrelationEngineService correlationEngineService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> mockTemplate = Mockito.mock(KafkaTemplate.class);
        this.kafkaTemplate = mockTemplate;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        CompletableFuture<SendResult<String, String>> successFuture = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(successFuture);

        this.eventProducer = new IncidentEventProducer(kafkaTemplate, objectMapper, "incidents");
        this.graphService = new DependencyGraphService();
        CorrelationSignalEvaluator evaluator = new CorrelationSignalEvaluator(graphService);
        IncidentFingerprinter fingerprinter = new IncidentFingerprinter();

        this.correlationEngineService = new CorrelationEngineService(
                graphService, evaluator, fingerprinter, eventProducer, 15, 30);
        this.tenantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Kafka publication: Emits IncidentCreated event with partition key tenant_id:root_service and valid PRD Section 51 envelope")
    void testIncidentKafkaPublication() throws Exception {
        Instant now = Instant.now();
        String rootService = "payment-service";

        // Setup topology: order-service -> payment-service
        graphService.recordServiceCall(tenantId, "order-service", rootService, false, 45.0, now);

        correlationEngineService.registerDeployment(new DeploymentEvent(
                tenantId, rootService, "v2.8", now.minusSeconds(180), "production", "commit-pay-28"));

        AnomalyRecord anomaly = new AnomalyRecord(
                UUID.randomUUID(), "fp-1", tenantId, UUID.randomUUID(), rootService, "db_connections",
                "production", "rule.resource_utilization", DetectorType.RULE_BASED, Severity.CRITICAL,
                AnomalyLifecycleState.RAISED, 95.0, 85.0, null, now,
                Map.of("message", "Hikari pool saturated", "status", "500", "trace_id", "trace-kafka-101")
        );

        Optional<CorrelatedIncidentGroup> result = correlationEngineService.correlateAnomaly(anomaly);
        assertThat(result).isPresent();

        // Capture Kafka ProducerRecord sent to KafkaTemplate
        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, String> recordSent = captor.getValue();
        assertThat(recordSent.topic()).isEqualTo("incidents");

        // Mandatory PRD Section 51 partition key: tenant_id:root_service
        String expectedKey = tenantId + ":" + rootService;
        assertThat(recordSent.key()).isEqualTo(expectedKey);

        // Deserialize and verify PRD Section 51 envelope structure
        String jsonPayload = recordSent.value();
        assertThat(jsonPayload).isNotNull().isNotBlank();

        KafkaEventEnvelope<CorrelatedIncidentPayload> envelope = objectMapper.readValue(
                jsonPayload, new TypeReference<>() {});

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.tenantId()).isEqualTo(tenantId);
        assertThat(envelope.environment()).isEqualTo("production");
        assertThat(envelope.schemaVersion()).isEqualTo("1.0");
        assertThat(envelope.producer()).isEqualTo("correlation-engine");
        assertThat(envelope.timestamp()).isNotNull();

        // Verify CorrelatedIncident payload contents
        CorrelatedIncidentPayload payload = envelope.payload();
        assertThat(payload.incidentId()).isEqualTo(result.get().getIncidentId());
        assertThat(payload.incidentFingerprint()).isEqualTo(result.get().getIncidentFingerprint());
        assertThat(payload.rootServiceCandidate()).isEqualTo(rootService);
        assertThat(payload.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(payload.status()).isEqualTo("DETECTED");
        assertThat(payload.primaryAnomalyId()).isEqualTo(anomaly.getAnomalyId());
        assertThat(payload.correlatedAnomalyIds()).containsExactly(anomaly.getAnomalyId());
        assertThat(payload.blastRadius()).isNotNull();
        assertThat(payload.candidateOrigins()).isNotEmpty();
        assertThat(payload.candidateOrigins().get(0).serviceId()).isEqualTo(rootService);

        // Verify in-memory tracking queue
        assertThat(eventProducer.getEmittedEvents()).hasSize(1);
    }
}
