package com.resolveiq.ingestion.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Metric payload conforming to OpenTelemetry canonical data model (PRD Section 13.2).
 */
public record MetricObservedPayload(
        @JsonProperty("metric_name") String metricName,
        @JsonProperty("metric_type") MetricType metricType,
        @JsonProperty("value") Double value,
        @JsonProperty("buckets") Map<Double, Long> buckets,
        @JsonProperty("labels") Map<String, String> labels,
        @JsonProperty("service_name") String serviceName,
        @JsonProperty("resource_attributes") Map<String, String> resourceAttributes,
        @JsonProperty("timestamp") Instant timestamp
) {
    public enum MetricType {
        GAUGE,
        COUNTER,
        HISTOGRAM
    }

    public MetricObservedPayload {
        if (labels == null) labels = Collections.emptyMap();
        if (resourceAttributes == null) resourceAttributes = Collections.emptyMap();
        if (buckets == null) buckets = Collections.emptyMap();
        if (timestamp == null) timestamp = Instant.now();
    }
}
