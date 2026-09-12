package com.resolveiq.processors.storage.opensearch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * OpenSearch canonical log document strictly matching logs-index-template.json (PRD §10.4, §13.1).
 */
public record LogDocument(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("environment") String environment,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("severity") String severity,
        @JsonProperty("message") String message,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("span_id") String spanId,
        @JsonProperty("attributes") Map<String, Object> attributes,
        @JsonProperty("resource_attributes") Map<String, String> resourceAttributes
) {}
