package com.resolveiq.processors.storage.opensearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpenSearchStorageTest {

    private InMemoryOpenSearchStorage storage;

    @BeforeEach
    void setUp() {
        storage = new InMemoryOpenSearchStorage();
    }

    @Test
    @DisplayName("Bulk index logs and verify tenant-isolated search")
    void testLogIndexingAndTenantIsolation() {
        UUID tenantAlpha = UUID.randomUUID();
        UUID tenantBeta = UUID.randomUUID();
        Instant now = Instant.now();

        LogDocument logAlpha = new LogDocument(
                UUID.randomUUID(),
                tenantAlpha,
                UUID.randomUUID(),
                "payment-service",
                "production",
                now,
                "ERROR",
                "Connection timeout to payment gateway",
                "trace-123",
                "span-456",
                Map.of("gateway", "stripe"),
                Map.of("host", "ip-10-0-1-12")
        );

        LogDocument logBeta = new LogDocument(
                UUID.randomUUID(),
                tenantBeta,
                UUID.randomUUID(),
                "payment-service",
                "production",
                now,
                "ERROR",
                "Tenant Beta private error log with secret info",
                "trace-999",
                "span-888",
                Map.of("account", "beta-enterprise"),
                Map.of("host", "ip-10-0-2-45")
        );

        int indexed = storage.indexLogs(List.of(logAlpha, logBeta));
        assertThat(indexed).isEqualTo(2);

        // Search Tenant Alpha
        List<LogDocument> alphaResults = storage.searchLogs(
                tenantAlpha, "payment-service", now.minusSeconds(60), now.plusSeconds(60),
                "gateway", "ERROR", 10);
        assertThat(alphaResults).hasSize(1);
        assertThat(alphaResults.get(0).message()).contains("Connection timeout");
        assertThat(alphaResults.get(0).tenantId()).isEqualTo(tenantAlpha);

        // Search Tenant Beta cannot see Tenant Alpha
        List<LogDocument> betaResults = storage.searchLogs(
                tenantBeta, "payment-service", now.minusSeconds(60), now.plusSeconds(60),
                null, null, 10);
        assertThat(betaResults).hasSize(1);
        assertThat(betaResults.get(0).tenantId()).isEqualTo(tenantBeta);
        assertThat(betaResults.get(0).message()).doesNotContain("gateway");
    }

    @Test
    @DisplayName("Bulk index trace spans and query by trace ID")
    void testTraceSpanIndexingAndQuery() {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();
        String traceId = "trace-checkout-987";

        TraceSpanDocument rootSpan = new TraceSpanDocument(
                UUID.randomUUID(),
                tenantId,
                null,
                "frontend-proxy",
                "production",
                traceId,
                "span-root",
                null,
                "POST /api/checkout",
                now.minusMillis(500),
                500L,
                "OK",
                Map.of("http.status_code", 200),
                Map.of()
        );

        TraceSpanDocument childSpan = new TraceSpanDocument(
                UUID.randomUUID(),
                tenantId,
                null,
                "payment-service",
                "production",
                traceId,
                "span-child-1",
                "span-root",
                "chargeCard",
                now.minusMillis(450),
                420L,
                "ERROR",
                Map.of("error.type", "TimeoutException"),
                Map.of()
        );

        int indexed = storage.indexSpans(List.of(rootSpan, childSpan));
        assertThat(indexed).isEqualTo(2);

        List<TraceSpanDocument> spans = storage.searchSpansByTraceId(tenantId, traceId);
        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).spanId()).isEqualTo("span-root");
        assertThat(spans.get(1).spanId()).isEqualTo("span-child-1");
        assertThat(spans.get(1).statusCode()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("Idempotent document re-indexing replaces rather than duplicates")
    void testIdempotentDocumentIndexing() {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        LogDocument doc1 = new LogDocument(
                eventId, tenantId, null, "auth-service", "production",
                now, "INFO", "User login attempt v1", null, null, Map.of(), Map.of()
        );
        LogDocument doc2 = new LogDocument(
                eventId, tenantId, null, "auth-service", "production",
                now, "INFO", "User login attempt v2 (updated)", null, null, Map.of(), Map.of()
        );

        storage.indexLogs(List.of(doc1));
        assertThat(storage.totalLogs(tenantId)).isEqualTo(1);

        // Re-index with identical event_id -> should update existing record
        storage.indexLogs(List.of(doc2));
        assertThat(storage.totalLogs(tenantId)).isEqualTo(1);

        List<LogDocument> results = storage.searchLogs(tenantId, "auth-service", now.minusSeconds(10), now.plusSeconds(10), null, null, 10);
        assertThat(results.get(0).message()).isEqualTo("User login attempt v2 (updated)");
    }
}
