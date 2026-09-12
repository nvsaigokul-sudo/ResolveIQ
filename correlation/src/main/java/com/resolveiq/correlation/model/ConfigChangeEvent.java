package com.resolveiq.correlation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Configuration / feature flag change event record for correlation signal evaluation (PRD §20).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfigChangeEvent(
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("change_key") String changeKey,
        @JsonProperty("changed_at") Instant changedAt,
        @JsonProperty("environment") String environment,
        @JsonProperty("details") String details
) {}
