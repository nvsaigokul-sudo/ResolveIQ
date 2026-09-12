package com.resolveiq.ingestion.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OtlpMetricsBatch(
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("service_name") String serviceName,
        @JsonProperty("environment") String environment,
        @JsonProperty("metrics") List<MetricObservedPayload> metrics,
        @JsonProperty("resource_attributes") Map<String, String> resourceAttributes
) {}
