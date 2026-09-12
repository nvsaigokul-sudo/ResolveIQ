package com.resolveiq.processors.storage.opensearch;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Storage contract for OpenSearch logs and traces with tenant routing and isolation (PRD §10.4, §13).
 */
public interface OpenSearchDocumentWriter {

    /**
     * Bulk indexes log documents with routing = tenant_id and document ID = event_id.
     */
    int indexLogs(List<LogDocument> logs);

    /**
     * Bulk indexes trace span documents with routing = tenant_id and document ID = event_id.
     */
    int indexSpans(List<TraceSpanDocument> spans);

    /**
     * Searches logs enforcing strict tenant isolation (Layer 1 + Layer 2 defense-in-depth).
     */
    List<LogDocument> searchLogs(UUID tenantId, String serviceId, Instant startTime, Instant endTime, String query, String severity, int limit);

    /**
     * Searches spans for a given trace ID enforcing tenant isolation.
     */
    List<TraceSpanDocument> searchSpansByTraceId(UUID tenantId, String traceId);

    /**
     * Searches spans for a given service within a time range enforcing tenant isolation.
     */
    List<TraceSpanDocument> searchSpans(UUID tenantId, String serviceId, Instant startTime, Instant endTime, int limit);
}
