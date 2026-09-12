package com.resolveiq.correlation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Service infrastructure mapping for common infrastructure signal evaluation (PRD §20).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServiceInfrastructureMetadata(
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("service_id") String serviceId,
        @JsonProperty("cluster") String cluster,
        @JsonProperty("host") String host,
        @JsonProperty("availability_zone") String availabilityZone,
        @JsonProperty("database_instance") String databaseInstance
) {}
