package com.resolveiq.backend.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.service.IncidentService;
import com.resolveiq.common.correlation.CorrelatedIncidentPayload;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer processing correlated incident events from Phase 5 (PRD §19.3, §51).
 * Consumes from the 'incidents' topic partitioned by tenant_id:root_service.
 */
@Component
public class IncidentKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(IncidentKafkaConsumer.class);

    private final IncidentService incidentService;
    private final ObjectMapper objectMapper;

    public IncidentKafkaConsumer(IncidentService incidentService, ObjectMapper objectMapper) {
        this.incidentService = incidentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${resolveiq.kafka.topics.incidents:incidents}",
            groupId = "${spring.kafka.consumer.group-id:resolveiq-incident-group}",
            autoStartup = "${spring.kafka.listener.auto-startup:true}"
    )
    public void consumeIncidentEvent(ConsumerRecord<String, String> record) {
        try {
            KafkaEventEnvelope<CorrelatedIncidentPayload> envelope = objectMapper.readValue(
                    record.value(),
                    new TypeReference<>() {}
            );

            if (envelope == null || envelope.payload() == null || envelope.tenantId() == null) {
                log.warn("Discarding malformed incident message from partition={}, offset={}", record.partition(), record.offset());
                return;
            }

            // Set tenant context derived strictly from authenticated Kafka envelope
            TenantContextHolder.setContext(TenantContext.ofSystem(envelope.tenantId(), "kafka-incident-consumer"));
            try {
                incidentService.createOrUpdateFromCorrelatedIncident(envelope.payload());
            } finally {
                TenantContextHolder.clear();
            }
        } catch (Exception e) {
            log.error("Failed to process incident event from topic={}, partition={}, offset={}: {}",
                    record.topic(), record.partition(), record.offset(), e.getMessage(), e);
        }
    }
}
