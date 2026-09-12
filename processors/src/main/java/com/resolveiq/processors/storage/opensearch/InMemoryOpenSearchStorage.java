package com.resolveiq.processors.storage.opensearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory high-fidelity implementation of OpenSearch document storage (ADR-008).
 * Enforces exact OpenSearch tenant routing, document ID upsert semantics,
 * and tenant-isolated search semantics for embedded execution and CI verification.
 */
@Repository
public class InMemoryOpenSearchStorage implements OpenSearchDocumentWriter {

    private static final Logger log = LoggerFactory.getLogger(InMemoryOpenSearchStorage.class);

    // Tenant-isolated storage: tenantId -> (documentId -> document)
    private final Map<UUID, Map<String, LogDocument>> tenantLogs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, TraceSpanDocument>> tenantSpans = new ConcurrentHashMap<>();

    @Override
    public int indexLogs(List<LogDocument> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (LogDocument doc : logs) {
            if (doc.tenantId() == null || doc.eventId() == null) {
                continue;
            }
            Map<String, LogDocument> logMap = tenantLogs.computeIfAbsent(doc.tenantId(), k -> new ConcurrentHashMap<>());
            logMap.put(doc.eventId().toString(), doc);
            count++;
        }
        log.debug("Bulk indexed {} log documents into OpenSearch storage", count);
        return count;
    }

    @Override
    public int indexSpans(List<TraceSpanDocument> spans) {
        if (spans == null || spans.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (TraceSpanDocument span : spans) {
            if (span.tenantId() == null || span.eventId() == null) {
                continue;
            }
            Map<String, TraceSpanDocument> spanMap = tenantSpans.computeIfAbsent(span.tenantId(), k -> new ConcurrentHashMap<>());
            // Key by traceId:spanId or eventId for idempotent indexing
            String key = span.traceId() != null && span.spanId() != null ?
                    span.traceId() + ":" + span.spanId() : span.eventId().toString();
            spanMap.put(key, span);
            count++;
        }
        log.debug("Bulk indexed {} trace span documents into OpenSearch storage", count);
        return count;
    }

    @Override
    public List<LogDocument> searchLogs(UUID tenantId, String serviceId, Instant startTime, Instant endTime, String query, String severity, int limit) {
        if (tenantId == null) {
            return Collections.emptyList();
        }

        Map<String, LogDocument> logMap = tenantLogs.get(tenantId);
        if (logMap == null || logMap.isEmpty()) {
            return Collections.emptyList();
        }

        return logMap.values().stream()
                .filter(doc -> serviceId == null || serviceId.equalsIgnoreCase(doc.serviceId()))
                .filter(doc -> startTime == null || !doc.timestamp().isBefore(startTime))
                .filter(doc -> endTime == null || !doc.timestamp().isAfter(endTime))
                .filter(doc -> severity == null || severity.equalsIgnoreCase(doc.severity()))
                .filter(doc -> query == null || query.isBlank() ||
                        (doc.message() != null && doc.message().toLowerCase().contains(query.toLowerCase())))
                .sorted(Comparator.comparing(LogDocument::timestamp).reversed())
                .limit(limit > 0 ? limit : 100)
                .collect(Collectors.toList());
    }

    @Override
    public List<TraceSpanDocument> searchSpansByTraceId(UUID tenantId, String traceId) {
        if (tenantId == null || traceId == null) {
            return Collections.emptyList();
        }

        Map<String, TraceSpanDocument> spanMap = tenantSpans.get(tenantId);
        if (spanMap == null) {
            return Collections.emptyList();
        }

        return spanMap.values().stream()
                .filter(span -> traceId.equalsIgnoreCase(span.traceId()))
                .sorted(Comparator.comparing(TraceSpanDocument::startTime))
                .collect(Collectors.toList());
    }

    @Override
    public List<TraceSpanDocument> searchSpans(UUID tenantId, String serviceId, Instant startTime, Instant endTime, int limit) {
        if (tenantId == null) {
            return Collections.emptyList();
        }

        Map<String, TraceSpanDocument> spanMap = tenantSpans.get(tenantId);
        if (spanMap == null) {
            return Collections.emptyList();
        }

        return spanMap.values().stream()
                .filter(span -> serviceId == null || serviceId.equalsIgnoreCase(span.serviceId()))
                .filter(span -> startTime == null || !span.startTime().isBefore(startTime))
                .filter(span -> endTime == null || !span.startTime().isAfter(endTime))
                .sorted(Comparator.comparing(TraceSpanDocument::startTime).reversed())
                .limit(limit > 0 ? limit : 100)
                .collect(Collectors.toList());
    }

    public void clear() {
        tenantLogs.clear();
        tenantSpans.clear();
    }

    public int totalLogs(UUID tenantId) {
        Map<String, LogDocument> m = tenantLogs.get(tenantId);
        return m != null ? m.size() : 0;
    }

    public int totalSpans(UUID tenantId) {
        Map<String, TraceSpanDocument> m = tenantSpans.get(tenantId);
        return m != null ? m.size() : 0;
    }
}
