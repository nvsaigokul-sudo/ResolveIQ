package com.resolveiq.detection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.telemetry.AnomalyDetectedPayload;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.detection.model.AnomalyRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Kafka Producer for AnomalyDetected events conforming to PRD §51.
 * Partitions by tenant_id:service_id ensuring ordered correlation by downstream engines.
 */
@Component
public class AnomalyEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AnomalyEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String anomalyTopic;

    // In-memory queue for verification and tests
    private final ConcurrentLinkedQueue<KafkaEventEnvelope<AnomalyDetectedPayload>> emittedEvents = new ConcurrentLinkedQueue<>();

    public AnomalyEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${resolveiq.kafka.topics.anomalies:telemetry.anomalies}") String anomalyTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.anomalyTopic = anomalyTopic;
    }

    public void publishAnomalyEvent(AnomalyRecord record) {
        if (record == null) return;

        AnomalyDetectedPayload payload = new AnomalyDetectedPayload(
                record.getAnomalyId(),
                record.getFingerprint(),
                record.getDetectorId(),
                record.getDetectorType(),
                record.getServiceId(),
                record.getMetricName(),
                record.getLastObservedValue(),
                record.getThresholdValue(),
                record.getSeverity(),
                record.getLifecycleState(),
                60, // Standard evaluation window
                record.getConsecutiveBreachCount(),
                record.getDeviationSigma(),
                record.getDetails(),
                record.getLastEvaluatedAt()
        );

        KafkaEventEnvelope<AnomalyDetectedPayload> envelope = new KafkaEventEnvelope<>(
                UUID.randomUUID(),
                record.getTenantId(),
                record.getProjectId(),
                record.getEnvironment(),
                Instant.now(),
                UUID.randomUUID().toString(),
                "1.0",
                "detection-engine",
                null,
                payload
        );

        emittedEvents.add(envelope);

        String partitionKey = record.getTenantId() + ":" + record.getServiceId();

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(new ProducerRecord<>(anomalyTopic, partitionKey, json))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish AnomalyDetected event for anomaly {}", record.getAnomalyId(), ex);
                        } else {
                            log.info("Published AnomalyDetected event {} to topic {} at offset {}",
                                    record.getAnomalyId(), anomalyTopic, result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to serialize AnomalyDetected event envelope for anomaly {}", record.getAnomalyId(), e);
        }
    }

    public ConcurrentLinkedQueue<KafkaEventEnvelope<AnomalyDetectedPayload>> getEmittedEvents() {
        return emittedEvents;
    }

    public void clear() {
        emittedEvents.clear();
    }
}
