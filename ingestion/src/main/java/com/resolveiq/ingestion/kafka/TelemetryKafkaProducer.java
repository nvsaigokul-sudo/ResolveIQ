package com.resolveiq.ingestion.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.exception.ResolveIQException;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Kafka Producer service conforming to PRD Section 10.1, 15.1, 51.
 * Partition Key: tenant_id:service_id (guarantees per-service ordering and prevents cross-tenant fan-in).
 * Handles backpressure (503 + Retry-After) and DLQ routing for poison messages.
 */
@Service
public class TelemetryKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(TelemetryKafkaProducer.class);

    public static final String TOPIC_METRICS = "telemetry.metrics";
    public static final String TOPIC_LOGS = "telemetry.logs";
    public static final String TOPIC_TRACES = "telemetry.traces";
    public static final String TOPIC_EVENTS = "system.events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final long produceTimeoutMs;
    private final List<DlqEntry> inMemoryDlqInspector = new CopyOnWriteArrayList<>();

    public TelemetryKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${resolveiq.ingestion.kafka.produce-timeout-ms:3000}") long produceTimeoutMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.produceTimeoutMs = produceTimeoutMs;
    }

    public record DlqEntry(
            String dlqTopic,
            String originalTopic,
            String partitionKey,
            String payload,
            String failureReason,
            Instant timestamp
    ) {}

    public <T> SendResult<String, String> send(String topic, String serviceId, KafkaEventEnvelope<T> envelope) {
        Objects.requireNonNull(topic, "topic cannot be null");
        Objects.requireNonNull(envelope, "envelope cannot be null");
        Objects.requireNonNull(envelope.tenantId(), "tenantId cannot be null in envelope");

        // Partition Key = tenant_id + ":" + service_id (PRD §10.1, §15.1)
        String partitionKey = envelope.tenantId().toString() + ":" + (serviceId != null ? serviceId : "default");

        String serializedJson;
        try {
            serializedJson = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("Failed to serialize telemetry envelope for tenant {}: {}", envelope.tenantId(), e.getMessage());
            sendToDlq(topic, partitionKey, envelope.toString(), "SERIALIZATION_FAILURE: " + e.getMessage());
            throw new ResolveIQException("TELEMETRY_SERIALIZATION_FAILED", "Failed to serialize telemetry: " + e.getMessage());
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, partitionKey, serializedJson);
        record.headers().add(new RecordHeader("tenant_id", envelope.tenantId().toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("schema_version", envelope.schemaVersion().getBytes(StandardCharsets.UTF_8)));

        try {
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record);
            return future.get(produceTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Kafka broker produce timeout ({} ms) for tenant {} on topic {}", produceTimeoutMs, envelope.tenantId(), topic);
            throw new IngestionBackpressureException("Ingestion buffer full / Kafka broker unavailable. Please back off and retry.", 5);
        } catch (Exception e) {
            log.error("Kafka produce error for tenant {}: {}", envelope.tenantId(), e.getMessage(), e);
            throw new IngestionBackpressureException("Kafka broker failure: " + e.getMessage(), 5);
        }
    }

    public void sendToDlq(String originalTopic, String partitionKey, String payload, String reason) {
        String dlqTopic = originalTopic + ".DLQ";
        log.warn("Routing message to DLQ [{}]: reason={}", dlqTopic, reason);

        ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(dlqTopic, partitionKey, payload);
        dlqRecord.headers().add(new RecordHeader("x-dlq-reason", reason.getBytes(StandardCharsets.UTF_8)));
        dlqRecord.headers().add(new RecordHeader("x-dlq-timestamp", Instant.now().toString().getBytes(StandardCharsets.UTF_8)));

        inMemoryDlqInspector.add(new DlqEntry(dlqTopic, originalTopic, partitionKey, payload, reason, Instant.now()));
        if (inMemoryDlqInspector.size() > 500) {
            inMemoryDlqInspector.remove(0);
        }

        try {
            kafkaTemplate.send(dlqRecord);
        } catch (Exception e) {
            log.error("Failed to route poison message to Kafka DLQ topic {}: {}", dlqTopic, e.getMessage());
        }
    }

    public List<DlqEntry> getInspectableDlqEntries() {
        return Collections.unmodifiableList(inMemoryDlqInspector);
    }
}
