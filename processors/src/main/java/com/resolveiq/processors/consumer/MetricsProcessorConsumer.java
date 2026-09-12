package com.resolveiq.processors.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.common.telemetry.MetricObservedPayload;
import com.resolveiq.processors.dedup.ProcessorIdempotencyCache;
import com.resolveiq.processors.kafka.ProcessorDlqProducer;
import com.resolveiq.processors.storage.timescale.MetricRecord;
import com.resolveiq.processors.storage.timescale.TimescaleMetricWriter;
import com.resolveiq.processors.watermark.WatermarkTracker;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-throughput, watermarked Kafka consumer for metrics telemetry (PRD §10.3, §13.2, §51).
 * Consumes from "telemetry.metrics", executes deduplication, computes watermarks,
 * and batch-inserts into TimescaleDB before manually acknowledging Kafka offsets.
 */
@Service
public class MetricsProcessorConsumer {

    private static final Logger log = LoggerFactory.getLogger(MetricsProcessorConsumer.class);

    private final TimescaleMetricWriter metricWriter;
    private final WatermarkTracker watermarkTracker;
    private final ProcessorIdempotencyCache idempotencyCache;
    private final ProcessorDlqProducer dlqProducer;
    private final ObjectMapper objectMapper;

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong duplicateCount = new AtomicLong(0);
    private final AtomicLong lateCount = new AtomicLong(0);

    public MetricsProcessorConsumer(
            TimescaleMetricWriter metricWriter,
            WatermarkTracker watermarkTracker,
            ProcessorIdempotencyCache idempotencyCache,
            ProcessorDlqProducer dlqProducer,
            ObjectMapper objectMapper) {
        this.metricWriter = metricWriter;
        this.watermarkTracker = watermarkTracker;
        this.idempotencyCache = idempotencyCache;
        this.dlqProducer = dlqProducer;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${resolveiq.kafka.topics.metrics:telemetry.metrics}",
            groupId = "${resolveiq.kafka.consumer.metrics-group:resolveiq-metrics-processor}",
            containerFactory = "kafkaManualAckContainerFactory"
    )
    public void processMetricsBatch(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        if (records == null || records.isEmpty()) {
            if (acknowledgment != null) acknowledgment.acknowledge();
            return;
        }

        List<MetricRecord> batchToWrite = new ArrayList<>(records.size());

        for (ConsumerRecord<String, String> record : records) {
            String rawJson = record.value();
            try {
                KafkaEventEnvelope<MetricObservedPayload> envelope = objectMapper.readValue(
                        rawJson, new TypeReference<>() {});

                // 1. Idempotent Deduplication Check
                if (!idempotencyCache.checkAndRegister(envelope.tenantId(), envelope.eventId())) {
                    duplicateCount.incrementAndGet();
                    log.debug("Skipping duplicate metric event {} for tenant {}", envelope.eventId(), envelope.tenantId());
                    continue;
                }

                MetricObservedPayload payload = envelope.payload();
                String serviceId = payload.serviceName() != null ? payload.serviceName() : "unknown-service";
                String streamKey = envelope.tenantId() + ":" + serviceId;
                Instant eventTime = payload.timestamp() != null ? payload.timestamp() : envelope.timestamp();

                // 2. Watermark and Late-Arrival Evaluation
                boolean isLate = watermarkTracker.isLate(streamKey, eventTime);
                if (isLate) {
                    lateCount.incrementAndGet();
                } else {
                    watermarkTracker.updateWatermark(streamKey, eventTime);
                }

                // 3. Normalization to Storage Record
                String dimensionsHash = TimescaleMetricWriter.computeDimensionsHash(payload.labels());
                double val = payload.value() != null ? payload.value() : 0.0;
                String typeStr = payload.metricType() != null ? payload.metricType().name() : "GAUGE";

                MetricRecord metricRecord = new MetricRecord(
                        eventTime,
                        envelope.tenantId(),
                        envelope.projectId(),
                        serviceId,
                        envelope.environment(),
                        payload.metricName(),
                        typeStr,
                        val,
                        payload.resourceAttributes().get("unit"),
                        payload.labels(),
                        dimensionsHash,
                        envelope.eventId(),
                        isLate,
                        Instant.now()
                );
                batchToWrite.add(metricRecord);

            } catch (Exception e) {
                log.error("Failed to parse metric envelope from partition {} offset {}. Routing to DLQ.",
                        record.partition(), record.offset(), e);
                dlqProducer.sendToDlq(
                        "telemetry.metrics.dlq",
                        record.key(),
                        rawJson,
                        "JSON/Envelope deserialization error: " + e.getMessage(),
                        record.topic(),
                        record.partition(),
                        record.offset()
                );
            }
        }

        // 4. Batch Persist into TimescaleDB
        if (!batchToWrite.isEmpty()) {
            try {
                int inserted = metricWriter.insertBatch(batchToWrite);
                processedCount.addAndGet(inserted);
                log.debug("Committed {} metric records to TimescaleDB", inserted);
            } catch (Exception dbEx) {
                log.error("CRITICAL: Failed to write metric batch to TimescaleDB. Backpressure engaged.", dbEx);
                // Do NOT acknowledge offset; throw exception to trigger Spring Kafka retry / backpressure
                throw new RuntimeException("TimescaleDB storage failure during metrics batch insertion", dbEx);
            }
        }

        // 5. Commit Kafka Offsets ONLY after successful database write
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    public long getProcessedCount() {
        return processedCount.get();
    }

    public long getDuplicateCount() {
        return duplicateCount.get();
    }

    public long getLateCount() {
        return lateCount.get();
    }

    public void resetCounts() {
        processedCount.set(0);
        duplicateCount.set(0);
        lateCount.set(0);
    }
}
