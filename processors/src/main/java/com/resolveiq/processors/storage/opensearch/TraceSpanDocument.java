package com.resolveiq.processors.storage.opensearch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * OpenSearch canonical trace span document strictly matching traces-index-template.json (PRD §10.4, §13.3).
 */
public record TraceSpanDocument(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("environment") String environment,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("span_id") String spanId,
        @JsonProperty("parent_span_id") String parentSpanId,
        @JsonProperty("operation_name") String operationName,
        @JsonProperty("start_time") Instant startTime,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("status_code") String statusCode,
        @JsonProperty("attributes") Map<String, Object> attributes,
        @JsonProperty("resource_attributes") Map<String, String> resourceAttributes
) {}
