package com.resolveiq.processors.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.common.telemetry.LogObservedPayload;
import com.resolveiq.common.telemetry.MetricObservedPayload;
import com.resolveiq.common.telemetry.TraceObservedPayload;
import com.resolveiq.processors.kafka.ProcessorDlqProducer;
import com.resolveiq.processors.storage.opensearch.LogDocument;
import com.resolveiq.processors.storage.opensearch.OpenSearchDocumentWriter;
import com.resolveiq.processors.storage.opensearch.TraceSpanDocument;
import com.resolveiq.processors.storage.timescale.MetricRecord;
import com.resolveiq.processors.storage.timescale.TimescaleMetricWriter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "telemetry.metrics",
                "telemetry.logs",
                "telemetry.traces",
                "telemetry.metrics.dlq",
                "telemetry.logs.dlq",
                "telemetry.traces.dlq"
        }
)
class ConsumerOffsetAndRecoveryTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private MetricsProcessorConsumer metricsConsumer;

    @Autowired
    private LogsProcessorConsumer logsConsumer;

    @Autowired
    private TracesProcessorConsumer tracesConsumer;

    @Autowired
    private TimescaleMetricWriter metricWriter;

    @Autowired
    private OpenSearchDocumentWriter openSearchWriter;

    @Autowired
    private ProcessorDlqProducer dlqProducer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM metrics_raw");
        metricsConsumer.resetCounts();
        logsConsumer.resetCounts();
        tracesConsumer.resetCounts();
        dlqProducer.clear();
    }

    @Test
    @DisplayName("Metrics consumer processes batch, writes to TimescaleDB, and deduplicates re-delivery")
    void testMetricsProcessingAndDeduplication() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        MetricObservedPayload payload = new MetricObservedPayload(
                "jvm_memory_used_bytes",
                MetricObservedPayload.MetricType.GAUGE,
                536870912.0,
                Map.of(),
                Map.of("area", "heap"),
                "order-service",
                Map.of(),
                now
        );

        KafkaEventEnvelope<MetricObservedPayload> envelope = new KafkaEventEnvelope<>(
                eventId,
                tenantId,
                UUID.randomUUID(),
                "production",
                now,
                "corr-1",
                "1.0",
                "ingestion-service",
                "trace-1",
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        String partitionKey = tenantId + ":order-service";

        // 1. Publish first time
        kafkaTemplate.send(new ProducerRecord<>("telemetry.metrics", partitionKey, json)).get(5, TimeUnit.SECONDS);

        // Await consumption and insertion into TimescaleDB
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(metricsConsumer.getProcessedCount()).isGreaterThanOrEqualTo(1);
        });

        List<MetricRecord> stored = metricWriter.queryMetrics(
                tenantId, "order-service", "jvm_memory_used_bytes",
                now.minusSeconds(10), now.plusSeconds(10));
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).metricValue()).isEqualTo(536870912.0);

        // 2. Publish same event ID again (simulating Kafka re-delivery / consumer restart)
        kafkaTemplate.send(new ProducerRecord<>("telemetry.metrics", partitionKey, json)).get(5, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(metricsConsumer.getDuplicateCount()).isGreaterThanOrEqualTo(1);
        });

        // Verify no duplicate row created
        List<MetricRecord> storedAfterDup = metricWriter.queryMetrics(
                tenantId, "order-service", "jvm_memory_used_bytes",
                now.minusSeconds(10), now.plusSeconds(10));
        assertThat(storedAfterDup).hasSize(1);
    }

    @Test
    @DisplayName("Logs consumer processes batch, writes to OpenSearch with tenant routing")
    void testLogsProcessingAndOpenSearchIndexing() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        LogObservedPayload payload = new LogObservedPayload(
                now,
                "ERROR",
                "Database pool exhausted during checkout",
                Map.of("active_connections", 100),
                "payment-service",
                "production",
                "trace-pay-1",
                "span-pay-1",
                Map.of()
        );

        KafkaEventEnvelope<LogObservedPayload> envelope = new KafkaEventEnvelope<>(
                eventId,
                tenantId,
                UUID.randomUUID(),
                "production",
                now,
                "corr-log-1",
                "1.0",
                "ingestion-service",
                "trace-pay-1",
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        String partitionKey = tenantId + ":payment-service";

        kafkaTemplate.send(new ProducerRecord<>("telemetry.logs", partitionKey, json)).get(5, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(logsConsumer.getProcessedCount()).isGreaterThanOrEqualTo(1);
        });

        List<LogDocument> searchResults = openSearchWriter.searchLogs(
                tenantId, "payment-service", now.minusSeconds(10), now.plusSeconds(10),
                "exhausted", "ERROR", 10);
        assertThat(searchResults).hasSize(1);
        assertThat(searchResults.get(0).message()).contains("Database pool exhausted");
    }

    @Test
    @DisplayName("Traces consumer processes batch and writes spans to OpenSearch")
    void testTracesProcessingAndSpanStorage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        String traceId = "trace-e2e-777";

        TraceObservedPayload payload = new TraceObservedPayload(
                traceId,
                "span-auth-1",
                null,
                "auth-service",
                "validateToken",
                now,
                35L,
                "OK",
                Map.of("user.role", "SRE"),
                Map.of()
        );

        KafkaEventEnvelope<TraceObservedPayload> envelope = new KafkaEventEnvelope<>(
                eventId,
                tenantId,
                UUID.randomUUID(),
                "production",
                now,
                "corr-trace-1",
                "1.0",
                "ingestion-service",
                traceId,
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);
        String partitionKey = tenantId + ":auth-service";

        kafkaTemplate.send(new ProducerRecord<>("telemetry.traces", partitionKey, json)).get(5, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(tracesConsumer.getProcessedCount()).isGreaterThanOrEqualTo(1);
        });

        List<TraceSpanDocument> spans = openSearchWriter.searchSpansByTraceId(tenantId, traceId);
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).operationName()).isEqualTo("validateToken");
        assertThat(spans.get(0).durationMs()).isEqualTo(35L);
    }

    @Test
    @DisplayName("Corrupt payload on telemetry topic is safely routed to DLQ without crashing consumer")
    void testCorruptPayloadRoutesToDlq() throws Exception {
        String invalidJson = "{\"not_a_valid_envelope\": true, broken_json: ...}";

        kafkaTemplate.send(new ProducerRecord<>("telemetry.metrics", "bad-key", invalidJson)).get(5, TimeUnit.SECONDS);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(dlqProducer.getInMemoryDlqStore()).isNotEmpty();
            Map<String, Object> dlqEntry = dlqProducer.getInMemoryDlqStore().peek();
            assertThat(dlqEntry.get("dlq_topic")).isEqualTo("telemetry.metrics.dlq");
            assertThat(dlqEntry.get("failure_reason").toString()).contains("deserialization error");
        });
    }
}
