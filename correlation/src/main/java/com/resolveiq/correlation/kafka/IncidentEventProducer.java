package com.resolveiq.correlation.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.correlation.CorrelatedIncidentPayload;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.correlation.model.CorrelatedIncidentGroup;
import com.resolveiq.detection.model.AnomalyRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Kafka Producer for IncidentCreated / IncidentCorrelated events (PRD §51).
 * Partitions by tenant_id:root_service guaranteeing ordered downstream consumption.
 */
@Component
public class IncidentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(IncidentEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String incidentTopic;

    // In-memory queue for verification and tests
    private final ConcurrentLinkedQueue<KafkaEventEnvelope<CorrelatedIncidentPayload>> emittedEvents = new ConcurrentLinkedQueue<>();

    public IncidentEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${resolveiq.kafka.topics.incidents:incidents}") String incidentTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.incidentTopic = incidentTopic;
    }

    public void publishIncidentEvent(CorrelatedIncidentGroup group) {
        if (group == null) return;

        List<UUID> anomalyIds = group.getCorrelatedAnomalies().stream()
                .map(AnomalyRecord::getAnomalyId)
                .toList();

        String title = String.format("Degradation on %s [%s]",
                group.getRootServiceCandidate(), group.getSeverity());

        CorrelatedIncidentPayload payload = new CorrelatedIncidentPayload(
                group.getIncidentId(),
                group.getIncidentFingerprint(),
                title,
                group.getRootServiceCandidate(),
                group.getSeverity(),
                group.getStatus(),
                group.getPrimaryAnomaly().getAnomalyId(),
                anomalyIds,
                group.getConfidenceTier(),
                group.getCompositeCorrelationScore(),
                group.getSignalBreakdown(),
                group.getBlastRadius(),
                group.getCandidateOrigins(),
                group.getCreatedAt()
        );

        KafkaEventEnvelope<CorrelatedIncidentPayload> envelope = new KafkaEventEnvelope<>(
                UUID.randomUUID(),
                group.getTenantId(),
                group.getProjectId(),
                group.getEnvironment(),
                Instant.now(),
                UUID.randomUUID().toString(),
                "1.0",
                "correlation-engine",
                null,
                payload
        );

        emittedEvents.add(envelope);

        String partitionKey = group.getTenantId() + ":" + group.getRootServiceCandidate();

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(new ProducerRecord<>(incidentTopic, partitionKey, json))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish IncidentEvent for incident {}", group.getIncidentId(), ex);
                        } else {
                            log.info("Published IncidentEvent {} to topic {} at offset {}",
                                    group.getIncidentId(), incidentTopic, result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize IncidentEvent envelope for incident {}", group.getIncidentId(), e);
        }
    }

    public ConcurrentLinkedQueue<KafkaEventEnvelope<CorrelatedIncidentPayload>> getEmittedEvents() {
        return emittedEvents;
    }

    public void clear() {
        emittedEvents.clear();
    }
}
