package com.resolveiq.ingestion.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Common Kafka Event Envelope strictly conforming to PRD Section 51.
 * Envelope fields: event_id (UUID), tenant_id, project_id, environment, timestamp,
 * correlation_id, schema_version, producer, trace_context, and type-specific payload.
 */
public record KafkaEventEnvelope<T>(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("environment") String environment,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("producer") String producer,
        @JsonProperty("trace_context") String traceContext,
        @JsonProperty("payload") T payload
) {
    public KafkaEventEnvelope {
        Objects.requireNonNull(eventId, "eventId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
    }

    public static <T> KafkaEventEnvelope<T> create(
            UUID tenantId,
            UUID projectId,
            String environment,
            String correlationId,
            String traceContext,
            T payload) {
        return new KafkaEventEnvelope<>(
                UUID.randomUUID(),
                tenantId,
                projectId,
                environment != null ? environment : "production",
                Instant.now(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                "1.0",
                "ingestion-service",
                traceContext,
                payload
        );
    }
}
