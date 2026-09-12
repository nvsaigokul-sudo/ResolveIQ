package com.resolveiq.common.telemetry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Log payload conforming to OpenTelemetry canonical data model (PRD Section 13.1).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LogObservedPayload(
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("severity") String severity,
        @JsonProperty("body") String body,
        @JsonProperty("attributes") Map<String, Object> attributes,
        @JsonProperty("service_name") String serviceName,
        @JsonProperty("deployment_environment") String deploymentEnvironment,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("span_id") String spanId,
        @JsonProperty("resource_attributes") Map<String, String> resourceAttributes
) {
    public LogObservedPayload {
        if (timestamp == null) timestamp = Instant.now();
        if (attributes == null) attributes = Collections.emptyMap();
        if (resourceAttributes == null) resourceAttributes = Collections.emptyMap();
    }
}
