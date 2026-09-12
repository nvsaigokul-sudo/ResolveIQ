package com.resolveiq.backend.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.common.incident.EvidenceCollectedPayload;
import com.resolveiq.common.incident.IncidentEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka event producer for incident and evidence lifecycle events (PRD §51).
 * Partition key: tenant_id:root_service to preserve per-service ordering.
 */
@Component
public class IncidentKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(IncidentKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${resolveiq.kafka.topics.incidents:incidents}")
    private String incidentsTopic;

    @Value("${resolveiq.kafka.topics.incident-events:incident.events}")
    private String incidentEventsTopic;

    @Value("${resolveiq.kafka.topics.evidence:telemetry.evidence}")
    private String evidenceTopic;

    public IncidentKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishIncidentCreated(IncidentEventPayload payload) {
        publishIncidentEnvelope("IncidentCreated", incidentsTopic, payload);
    }

    public void publishIncidentUpdated(IncidentEventPayload payload) {
        publishIncidentEnvelope("IncidentUpdated", incidentEventsTopic, payload);
    }

    public void publishEvidenceCollected(EvidenceCollectedPayload payload) {
        try {
            KafkaEventEnvelope<EvidenceCollectedPayload> envelope = new KafkaEventEnvelope<>(
                    UUID.randomUUID(),
                    payload.tenantId(),
                    null,
                    "production",
                    Instant.now(),
                    "resolveiq-backend",
                    "1.0",
                    "evidence-builder",
                    null,
                    payload
            );

            String partitionKey = payload.tenantId() + ":" + payload.service();
            String jsonPayload = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(evidenceTopic, partitionKey, jsonPayload);
            log.info("Published EvidenceCollected event: evidenceId={}, incidentId={}, tenantId={}",
                    payload.evidenceId(), payload.incidentId(), payload.tenantId());
        } catch (Exception e) {
            log.warn("Failed to publish EvidenceCollected Kafka event: {}", e.getMessage());
        }
    }

    public void publishInvestigationStarted(UUID tenantId, UUID incidentId, UUID investigationId, String rootService) {
        try {
            KafkaEventEnvelope<Map<String, Object>> envelope = new KafkaEventEnvelope<>(
                    UUID.randomUUID(),
                    tenantId,
                    null,
                    "production",
                    Instant.now(),
                    "resolveiq-backend",
                    "1.0",
                    "investigation-service",
                    null,
                    Map.of(
                            "incidentId", incidentId.toString(),
                            "investigationId", investigationId.toString(),
                            "rootService", rootService != null ? rootService : "unknown",
                            "status", "IN_PROGRESS"
                    )
            );
            String partitionKey = tenantId + ":" + (rootService != null ? rootService : "default");
            kafkaTemplate.send("investigations", partitionKey, objectMapper.writeValueAsString(envelope));
            log.info("Published InvestigationStarted event: investigationId={}, incidentId={}", investigationId, incidentId);
        } catch (Exception e) {
            log.warn("Failed to publish InvestigationStarted Kafka event: {}", e.getMessage());
        }
    }

    public void publishInvestigationCompleted(UUID tenantId, UUID incidentId, UUID investigationId, String status, int candidateCount) {
        try {
            KafkaEventEnvelope<Map<String, Object>> envelope = new KafkaEventEnvelope<>(
                    UUID.randomUUID(),
                    tenantId,
                    null,
                    "production",
                    Instant.now(),
                    "resolveiq-backend",
                    "1.0",
                    "investigation-service",
                    null,
                    Map.of(
                            "incidentId", incidentId.toString(),
                            "investigationId", investigationId.toString(),
                            "status", status,
                            "candidateCount", candidateCount
                    )
            );
            String partitionKey = tenantId + ":default";
            kafkaTemplate.send("investigations", partitionKey, objectMapper.writeValueAsString(envelope));
            log.info("Published InvestigationCompleted event: investigationId={}, incidentId={}, status={}", investigationId, incidentId, status);
        } catch (Exception e) {
            log.warn("Failed to publish InvestigationCompleted Kafka event: {}", e.getMessage());
        }
    }

    private void publishIncidentEnvelope(String eventType, String topic, IncidentEventPayload payload) {
        try {
            KafkaEventEnvelope<IncidentEventPayload> envelope = new KafkaEventEnvelope<>(
                    UUID.randomUUID(),
                    payload.tenantId(),
                    payload.projectId(),
                    "production",
                    Instant.now(),
                    "resolveiq-backend",
                    "1.0",
                    "incident-service",
                    null,
                    payload
            );

            String partitionKey = payload.tenantId() + ":" + (payload.rootService() != null ? payload.rootService() : "default");
            String jsonPayload = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, partitionKey, jsonPayload);
            log.info("Published {} event: incidentId={}, tenantId={}, status={}",
                    eventType, payload.incidentId(), payload.tenantId(), payload.status());
        } catch (Exception e) {
            log.warn("Failed to publish {} Kafka event: {}", eventType, e.getMessage());
        }
    }
}
