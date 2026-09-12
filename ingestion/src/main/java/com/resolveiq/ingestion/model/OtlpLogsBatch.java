package com.resolveiq.ingestion.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.resolveiq.common.telemetry.LogObservedPayload;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpLogsBatch(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("service_name") String serviceName,
        @JsonProperty("environment") String environment,
        @JsonProperty("logs") List<LogObservedPayload> logs,
        @JsonProperty("resource_attributes") Map<String, String> resourceAttributes
) {}
