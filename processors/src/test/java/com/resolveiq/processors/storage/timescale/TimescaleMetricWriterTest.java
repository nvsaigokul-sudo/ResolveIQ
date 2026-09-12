package com.resolveiq.processors.storage.timescale;

import com.resolveiq.processors.storage.timescale.TimescaleMetricWriter.RollupPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TimescaleMetricWriterTest {

    @Autowired
    private TimescaleMetricWriter metricWriter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM metrics_raw");
    }

    @Test
    @DisplayName("Batch metrics insertion and tenant isolation query")
    void testBatchInsertAndTenantIsolation() {
        UUID tenantAlpha = UUID.randomUUID();
        UUID tenantBeta = UUID.randomUUID();
        Instant now = Instant.now();

        MetricRecord alphaMetric1 = new MetricRecord(
                now.minusSeconds(30),
                tenantAlpha,
                UUID.randomUUID(),
                "payment-service",
                "production",
                "http_request_duration_ms",
                "HISTOGRAM",
                120.5,
                "ms",
                Map.of("route", "/checkout", "status", "200"),
                TimescaleMetricWriter.computeDimensionsHash(Map.of("route", "/checkout", "status", "200")),
                UUID.randomUUID(),
                false,
                now
        );

        MetricRecord alphaMetric2 = new MetricRecord(
                now.minusSeconds(10),
                tenantAlpha,
                UUID.randomUUID(),
                "payment-service",
                "production",
                "http_request_duration_ms",
                "HISTOGRAM",
                145.0,
                "ms",
                Map.of("route", "/checkout", "status", "200"),
                TimescaleMetricWriter.computeDimensionsHash(Map.of("route", "/checkout", "status", "200")),
                UUID.randomUUID(),
                false,
                now
        );

        MetricRecord betaMetric = new MetricRecord(
                now.minusSeconds(20),
                tenantBeta,
                UUID.randomUUID(),
                "payment-service",
                "production",
                "http_request_duration_ms",
                "HISTOGRAM",
                999.0,
                "ms",
                Map.of("route", "/checkout", "status", "500"),
                TimescaleMetricWriter.computeDimensionsHash(Map.of("route", "/checkout", "status", "500")),
                UUID.randomUUID(),
                false,
                now
        );

        int inserted = metricWriter.insertBatch(List.of(alphaMetric1, alphaMetric2, betaMetric));
        assertThat(inserted).isEqualTo(3);

        // Query Tenant Alpha -> exactly 2 records, zero Tenant Beta leakage
        List<MetricRecord> alphaResults = metricWriter.queryMetrics(
                tenantAlpha, "payment-service", "http_request_duration_ms",
                now.minusSeconds(60), now);
        assertThat(alphaResults).hasSize(2);
        assertThat(alphaResults).allMatch(m -> m.tenantId().equals(tenantAlpha));
        assertThat(alphaResults.get(0).metricValue()).isEqualTo(120.5);
        assertThat(alphaResults.get(1).metricValue()).isEqualTo(145.0);

        // Query Tenant Beta -> exactly 1 record, zero Tenant Alpha leakage
        List<MetricRecord> betaResults = metricWriter.queryMetrics(
                tenantBeta, "payment-service", "http_request_duration_ms",
                now.minusSeconds(60), now);
        assertThat(betaResults).hasSize(1);
        assertThat(betaResults.get(0).tenantId()).isEqualTo(tenantBeta);
        assertThat(betaResults.get(0).metricValue()).isEqualTo(999.0);
    }

    @Test
    @DisplayName("Idempotent ON CONFLICT resolution prevents duplicate metric rows")
    void testIdempotentDuplicateInsertion() {
        UUID tenantId = UUID.randomUUID();
        Instant fixedTime = Instant.parse("2026-09-12T12:00:00Z");
        UUID eventId = UUID.randomUUID();
        Map<String, String> dims = Map.of("env", "prod");
        String hash = TimescaleMetricWriter.computeDimensionsHash(dims);

        MetricRecord record = new MetricRecord(
                fixedTime,
                tenantId,
                null,
                "auth-service",
                "production",
                "cpu_utilization",
                "GAUGE",
                0.78,
                "percent",
                dims,
                hash,
                eventId,
                false,
                fixedTime
        );

        // First insertion
        int firstInsert = metricWriter.insertBatch(List.of(record));
        assertThat(firstInsert).isEqualTo(1);

        // Second insertion with identical primary key coordinates -> idempotent upsert/no-op
        metricWriter.insertBatch(List.of(record));

        List<MetricRecord> results = metricWriter.queryMetrics(
                tenantId, "auth-service", "cpu_utilization",
                fixedTime.minusSeconds(10), fixedTime.plusSeconds(10));
        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("Continuous rollup query calculates aggregate statistics")
    void testRollupAggregation() {
        UUID tenantId = UUID.randomUUID();
        Instant t = Instant.parse("2026-09-12T12:00:00Z");

        MetricRecord m1 = new MetricRecord(
                t, tenantId, null, "order-service", "production",
                "db_pool_active_connections", "GAUGE", 10.0, "count",
                Map.of(), "empty", UUID.randomUUID(), false, t
        );
        MetricRecord m2 = new MetricRecord(
                t, tenantId, null, "order-service", "production",
                "db_pool_active_connections", "GAUGE", 30.0, "count",
                Map.of(), "empty", UUID.randomUUID(), false, t
        );

        metricWriter.insertBatch(List.of(m1, m2));

        List<RollupPoint> rollups = metricWriter.queryRollup(
                tenantId, "order-service", "db_pool_active_connections", "1m",
                t.minusSeconds(60), t.plusSeconds(60));

        assertThat(rollups).isNotEmpty();
        RollupPoint point = rollups.get(0);
        assertThat(point.sampleCount()).isEqualTo(2);
        assertThat(point.valMin()).isEqualTo(10.0);
        assertThat(point.valMax()).isEqualTo(30.0);
        assertThat(point.valAvg()).isEqualTo(20.0);
        assertThat(point.valSum()).isEqualTo(40.0);
    }
}
