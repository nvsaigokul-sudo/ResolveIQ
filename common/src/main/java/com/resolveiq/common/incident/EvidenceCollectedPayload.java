package com.resolveiq.common.incident;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event payload for EvidenceCollected events (PRD §51).
 */
public record EvidenceCollectedPayload(
        @JsonProperty("evidence_id") UUID evidenceId,
        @JsonProperty("incident_id") UUID incidentId,
        @JsonProperty("tenant_id") UUID tenantId,
        @JsonProperty("source") EvidenceSource source,
        @JsonProperty("service") String service,
        @JsonProperty("query_used") String queryUsed,
        @JsonProperty("result_reference") String resultReference,
        @JsonProperty("payload_summary") String payloadSummary,
        @JsonProperty("relevance_score") Double relevanceScore,
        @JsonProperty("confidence") Double confidence,
        @JsonProperty("timestamp") Instant timestamp
) {}
