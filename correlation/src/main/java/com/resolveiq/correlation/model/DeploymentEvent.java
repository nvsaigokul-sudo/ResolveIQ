package com.resolveiq.correlation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Deployment event record for correlation signal evaluation (PRD §20).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeploymentEvent(
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("version") String version,
        @JsonProperty("deployed_at") Instant deployedAt,
        @JsonProperty("environment") String environment,
        @JsonProperty("commit_hash") String commitHash
) {}
