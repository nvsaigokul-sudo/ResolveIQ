package com.resolveiq.common.incident;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event payload for IncidentCreated and IncidentUpdated events (PRD §51).
 */
public record IncidentEventPayload(
        @JsonProperty("incident_id") UUID incidentId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("project_id") UUID projectId,
        @JsonProperty("fingerprint") String fingerprint,
        @JsonProperty("title") String title,
        @JsonProperty("status") IncidentStatus status,
        @JsonProperty("severity") IncidentSeverity severity,
        @JsonProperty("priority") String priority,
        @JsonProperty("root_service") String rootService,
        @JsonProperty("affected_services") List<String> affectedServices,
        @JsonProperty("owning_team") String owningTeam,
        @JsonProperty("assignee_id") UUID assigneeId,
        @JsonProperty("notes") String notes,
        @JsonProperty("timestamp") Instant timestamp
) {}
