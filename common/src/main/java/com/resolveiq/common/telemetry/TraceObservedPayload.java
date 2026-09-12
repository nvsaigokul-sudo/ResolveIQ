package com.resolveiq.common.telemetry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Trace span payload conforming to OpenTelemetry canonical data model (PRD Section 13.3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TraceObservedPayload(
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("span_id") String spanId,
        @JsonProperty("parent_span_id") String parentSpanId,
        @JsonProperty("service_name") String serviceName,
        @JsonProperty("operation_name") String operationName,
        @JsonProperty("start_time") Instant startTime,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("status") String status,
        @JsonProperty("attributes") Map<String, Object> attributes,
        @JsonProperty("resource_attributes") Map<String, String> resourceAttributes
) {
    public TraceObservedPayload {
        if (attributes == null) attributes = Collections.emptyMap();
        if (resourceAttributes == null) resourceAttributes = Collections.emptyMap();
        if (status == null) status = "OK";
    }
}
