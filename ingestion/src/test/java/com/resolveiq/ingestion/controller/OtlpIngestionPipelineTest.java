package com.resolveiq.ingestion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.crypto.ApiKeyGenerator;
import com.resolveiq.common.security.Role;
import com.resolveiq.ingestion.kafka.TelemetryKafkaProducer;
import com.resolveiq.common.telemetry.*;
import com.resolveiq.ingestion.model.*;
import com.resolveiq.ingestion.security.IngestionCredentialValidator;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {
                TelemetryKafkaProducer.TOPIC_METRICS,
                TelemetryKafkaProducer.TOPIC_LOGS,
                TelemetryKafkaProducer.TOPIC_TRACES,
                TelemetryKafkaProducer.TOPIC_EVENTS,
                "telemetry.metrics.DLQ",
                "telemetry.logs.DLQ",
                "telemetry.traces.DLQ"
        }
)
@DirtiesContext
class OtlpIngestionPipelineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestionCredentialValidator credentialValidator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private UUID tenantAlphaId;
    private UUID tenantBetaId;
    private String apiKeyAlpha;
    private Consumer<String, String> testConsumer;

    @BeforeEach
    void setUp() {
        tenantAlphaId = UUID.randomUUID();
        tenantBetaId = UUID.randomUUID();

        ApiKeyGenerator.GeneratedApiKey generated = ApiKeyGenerator.generate();
        apiKeyAlpha = generated.fullKey();

        // Register collector credential for Tenant Alpha
        credentialValidator.registerApiKey(
                apiKeyAlpha,
                tenantAlphaId,
                UUID.randomUUID(),
                generated.hashedSecret(),
                Role.ADMIN
        );

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-group-" + UUID.randomUUID(),
                "true",
                embeddedKafkaBroker
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        testConsumer = cf.createConsumer();
    }

    @AfterEach
    void tearDown() {
        if (testConsumer != null) {
            testConsumer.close();
        }
    }

    private ConsumerRecord<String, String> getLatestRecord(Consumer<String, String> consumer, String topic) {
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        java.util.List<ConsumerRecord<String, String>> list = new java.util.ArrayList<>();
        records.records(topic).forEach(list::add);
        assertThat(list).as("Expected records on topic " + topic).isNotEmpty();
        return list.get(list.size() - 1);
    }

    @Test
    @DisplayName("Acceptance Criteria 1, 3, 7: Ingests OTLP metrics, normalizes to canonical model, and partitions by tenant_id:service_id")
    void testMetricsIngestionAndPartitionKey() throws Exception {
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, TelemetryKafkaProducer.TOPIC_METRICS);

        MetricObservedPayload metric = new MetricObservedPayload(
                "http.server.duration",
                MetricObservedPayload.MetricType.GAUGE,
                142.5,
                null,
                Map.of("http.status_code", "200"),
                "order-service",
                Map.of("host.name", "worker-1"),
                Instant.now()
        );

        OtlpMetricsBatch batch = new OtlpMetricsBatch(
                UUID.randomUUID(),
                "order-service",
                "production",
                List.of(metric),
                Map.of("region", "us-east-1")
        );

        mockMvc.perform(post("/v1/metrics")
                        .header("X-API-Key", apiKeyAlpha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.ingested_count").value(1));

        // Poll Kafka and assert partition key and message envelope
        ConsumerRecord<String, String> record = getLatestRecord(testConsumer, TelemetryKafkaProducer.TOPIC_METRICS);

        assertThat(record).isNotNull();
        // Partition Key must be tenant_id:service_id (PRD §10.1, §15.1)
        assertThat(record.key()).isEqualTo(tenantAlphaId.toString() + ":order-service");

        // Envelope validation (PRD §51)
        String valueJson = record.value();
        assertThat(valueJson).contains("\"schema_version\":\"1.0\"");
        assertThat(valueJson).contains("\"producer\":\"ingestion-service\"");
        assertThat(valueJson).contains(tenantAlphaId.toString());
        assertThat(valueJson).contains("http.server.duration");
    }

    @Test
    @DisplayName("Acceptance Criteria 2, 12: Tenant context cannot be overridden by client X-Tenant-Id header")
    void testHeaderTamperingImmunity() throws Exception {
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, TelemetryKafkaProducer.TOPIC_LOGS);

        LogObservedPayload logItem = new LogObservedPayload(
                Instant.now(),
                "INFO",
                "Payment successfully authorized",
                Collections.emptyMap(),
                "payment-service",
                "production",
                "trace-101",
                "span-202",
                Collections.emptyMap()
        );

        OtlpLogsBatch batch = new OtlpLogsBatch(
                UUID.randomUUID(),
                "payment-service",
                "production",
                List.of(logItem),
                Collections.emptyMap()
        );

        // Client authenticates as Tenant Alpha but maliciously sends X-Tenant-Id for Tenant Beta
        mockMvc.perform(post("/v1/logs")
                        .header("X-API-Key", apiKeyAlpha)
                        .header("X-Tenant-Id", tenantBetaId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        ConsumerRecord<String, String> record = getLatestRecord(testConsumer, TelemetryKafkaProducer.TOPIC_LOGS);

        // Partition key and tenant_id must strictly match Tenant Alpha, NEVER Tenant Beta
        assertThat(record.key()).isEqualTo(tenantAlphaId.toString() + ":payment-service");
        assertThat(record.value()).contains(tenantAlphaId.toString());
        assertThat(record.value()).doesNotContain(tenantBetaId.toString());
    }

    @Test
    @DisplayName("Acceptance Criteria 4, 5, 13: Defense-in-depth PII and secret redaction before entering Kafka")
    void testDefenseInDepthPiiRedaction() throws Exception {
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, TelemetryKafkaProducer.TOPIC_LOGS);

        String rawSecret = "User priya.oncall@resolveiq.io checked out with card 4111-2222-3333-4444 using key riq_live_secret1234567890abcdef";
        LogObservedPayload logWithPii = new LogObservedPayload(
                Instant.now(),
                "ERROR",
                rawSecret,
                Map.of("password", "super-secret-password"),
                "checkout-service",
                "production",
                "trace-pii",
                "span-pii",
                Collections.emptyMap()
        );

        OtlpLogsBatch batch = new OtlpLogsBatch(
                UUID.randomUUID(),
                "checkout-service",
                "production",
                List.of(logWithPii),
                Collections.emptyMap()
        );

        mockMvc.perform(post("/v1/logs")
                        .header("X-API-Key", apiKeyAlpha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk());

        ConsumerRecord<String, String> record = getLatestRecord(testConsumer, TelemetryKafkaProducer.TOPIC_LOGS);

        String kafkaPayload = record.value();

        // Raw sensitive values must NOT appear in Kafka
        assertThat(kafkaPayload).doesNotContain("priya.oncall@resolveiq.io");
        assertThat(kafkaPayload).doesNotContain("4111-2222-3333-4444");
        assertThat(kafkaPayload).doesNotContain("riq_live_secret1234567890abcdef");
        assertThat(kafkaPayload).doesNotContain("super-secret-password");

        // Redacted tokens must be present
        assertThat(kafkaPayload).contains("[REDACTED_EMAIL]");
        assertThat(kafkaPayload).contains("[REDACTED_CREDIT_CARD]");
        assertThat(kafkaPayload).contains("[REDACTED_API_KEY]");
        assertThat(kafkaPayload).contains("[REDACTED_SECRET]");
    }

    @Test
    @DisplayName("Acceptance Criterion 6: Token-bucket rate limiting enforces HTTP 429 and Retry-After header")
    void testRateLimitExceededReturns429() throws Exception {
        // In test configuration, capacity = 5 tokens. Consuming 6 logs in one burst exceeds bucket.
        List<LogObservedPayload> burstLogs = List.of(
                new LogObservedPayload(Instant.now(), "INFO", "msg1", Map.of(), "s", "prod", "t", "s", Map.of()),
                new LogObservedPayload(Instant.now(), "INFO", "msg2", Map.of(), "s", "prod", "t", "s", Map.of()),
                new LogObservedPayload(Instant.now(), "INFO", "msg3", Map.of(), "s", "prod", "t", "s", Map.of()),
                new LogObservedPayload(Instant.now(), "INFO", "msg4", Map.of(), "s", "prod", "t", "s", Map.of()),
                new LogObservedPayload(Instant.now(), "INFO", "msg5", Map.of(), "s", "prod", "t", "s", Map.of()),
                new LogObservedPayload(Instant.now(), "INFO", "msg6", Map.of(), "s", "prod", "t", "s", Map.of())
        );

        OtlpLogsBatch batch = new OtlpLogsBatch(UUID.randomUUID(), "s", "prod", burstLogs, Map.of());

        mockMvc.perform(post("/v1/logs")
                        .header("X-API-Key", apiKeyAlpha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("TENANT_RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    @DisplayName("Acceptance Criterion 9: Event ID deduplication prevents duplicate processing in Kafka")
    void testIdempotencyDeduplication() throws Exception {
        UUID eventId = UUID.randomUUID();

        LogObservedPayload logItem = new LogObservedPayload(
                Instant.now(),
                "INFO",
                "Idempotent log message",
                Map.of(),
                "order-service",
                "production",
                "t",
                "s",
                Map.of()
        );

        OtlpLogsBatch batch = new OtlpLogsBatch(eventId, "order-service", "production", List.of(logItem), Map.of());

        // First attempt succeeds
        mockMvc.perform(post("/v1/logs")
                        .header("X-API-Key", apiKeyAlpha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.ingested_count").value(1));

        // Duplicate attempt with same eventId is dropped at deduplication cache
        mockMvc.perform(post("/v1/logs")
                        .header("X-API-Key", apiKeyAlpha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEDUPLICATED"))
                .andExpect(jsonPath("$.ingested_count").value(0));
    }

    @Test
    @DisplayName("Rejects unauthorized requests without valid credentials with HTTP 401")
    void testMissingAuthRejected() throws Exception {
        OtlpLogsBatch batch = new OtlpLogsBatch(UUID.randomUUID(), "s", "prod", List.of(), Map.of());

        mockMvc.perform(post("/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
