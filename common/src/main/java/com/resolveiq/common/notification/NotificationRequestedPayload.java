package com.resolveiq.common.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka event payload for NotificationRequested (PRD §51).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationRequestedPayload(
        @JsonProperty("notification_id") UUID notificationId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("incident_id") UUID incidentId,
        @JsonProperty("channel_id") UUID channelId,
        @JsonProperty("channel_type") NotificationChannelType channelType,
        @JsonProperty("destination") String destination,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("severity") String severity,
        @JsonProperty("subject") String subject,
        @JsonProperty("message") String message,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("created_at") Instant createdAt
) {}
