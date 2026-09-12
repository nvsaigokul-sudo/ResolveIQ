package com.resolveiq.common.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event payload for NotificationFailed (PRD §51).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationFailedPayload(
        @JsonProperty("notification_id") UUID notificationId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("incident_id") UUID incidentId,
        @JsonProperty("channel_id") UUID channelId,
        @JsonProperty("channel_type") NotificationChannelType channelType,
        @JsonProperty("destination") String destination,
        @JsonProperty("attempt_count") int attemptCount,
        @JsonProperty("final_status") NotificationDeliveryStatus finalStatus,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("failed_at") Instant failedAt
) {}
