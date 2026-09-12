package com.resolveiq.backend.notifications.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.notification.NotificationDeliveredPayload;
import com.resolveiq.common.notification.NotificationFailedPayload;
import com.resolveiq.common.notification.NotificationRequestedPayload;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka producer for notification lifecycle events conforming to PRD §51.
 */
@Component
public class NotificationKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaProducer.class);

    public static final String TOPIC_NOTIFICATIONS = "notifications";
    public static final String TOPIC_DELIVERED = "notifications.delivered";
    public static final String TOPIC_FAILED = "notifications.failed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public NotificationKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishNotificationRequested(NotificationRequestedPayload payload) {
        publishEvent(TOPIC_NOTIFICATIONS, payload.tenantId(), payload.channelType().name(), payload);
    }

    public void publishNotificationDelivered(NotificationDeliveredPayload payload) {
        publishEvent(TOPIC_DELIVERED, payload.tenantId(), payload.channelType().name(), payload);
    }

    public void publishNotificationFailed(NotificationFailedPayload payload) {
        publishEvent(TOPIC_FAILED, payload.tenantId(), payload.channelType().name(), payload);
    }

    private <T> void publishEvent(String topic, UUID tenantId, String serviceKey, T payload) {
        try {
            KafkaEventEnvelope<T> envelope = KafkaEventEnvelope.create(
                    tenantId,
                    null,
                    "production",
                    UUID.randomUUID().toString(),
                    null,
                    payload
            );
            String json = objectMapper.writeValueAsString(envelope);
            String partitionKey = tenantId.toString() + ":" + (serviceKey != null ? serviceKey : "default");

            kafkaTemplate.send(topic, partitionKey, json)
                    .whenComplete((res, ex) -> {
                        if (ex != null) {
                            log.warn("Non-fatal: Failed to publish to topic {}: {}", topic, ex.getMessage());
                        } else {
                            log.debug("Published event to topic {} [partitionKey={}]", topic, partitionKey);
                        }
                    });
        } catch (Exception e) {
            log.warn("Non-fatal: Failed to serialize or publish notification event to topic {}: {}", topic, e.getMessage());
        }
    }
}
