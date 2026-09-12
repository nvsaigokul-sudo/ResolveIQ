package com.resolveiq.ingestion.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record IngestionResponse(
        @JsonProperty("status") String status,
        @JsonProperty("event_id") UUID eventId,
        @JsonProperty("ingested_count") int ingestedCount,
        @JsonProperty("trace_id") String traceId
) {
    public static IngestionResponse success(UUID eventId, int count, String traceId) {
        return new IngestionResponse("SUCCESS", eventId, count, traceId);
    }

    public static IngestionResponse deduplicated(UUID eventId, String traceId) {
        return new IngestionResponse("DEDUPLICATED", eventId, 0, traceId);
    }
}
