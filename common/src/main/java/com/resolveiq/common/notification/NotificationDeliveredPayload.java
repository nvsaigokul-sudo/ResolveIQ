package com.resolveiq.common.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event payload for NotificationDelivered (PRD §51).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationDeliveredPayload(
        @JsonProperty("notification_id") UUID notificationId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("incident_id") UUID incidentId,
        @JsonProperty("channel_id") UUID channelId,
        @JsonProperty("channel_type") NotificationChannelType channelType,
        @JsonProperty("destination") String destination,
        @JsonProperty("attempt_count") int attemptCount,
        @JsonProperty("delivered_at") Instant deliveredAt
) {}
