package com.resolveiq.processors.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.common.telemetry.TraceObservedPayload;
import com.resolveiq.processors.dedup.ProcessorIdempotencyCache;
import com.resolveiq.processors.kafka.ProcessorDlqProducer;
import com.resolveiq.processors.storage.opensearch.OpenSearchDocumentWriter;
import com.resolveiq.processors.storage.opensearch.TraceSpanDocument;
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
 * High-throughput Kafka consumer for trace span telemetry (PRD §10.4, §13.3, §51).
 * Consumes from "telemetry.traces", executes deduplication and watermarking,
 * and bulk indexes into OpenSearch with tenant routing before manually acknowledging Kafka offsets.
 */
@Service
public class TracesProcessorConsumer {

    private static final Logger log = LoggerFactory.getLogger(TracesProcessorConsumer.class);

    private final OpenSearchDocumentWriter openSearchWriter;
    private final WatermarkTracker watermarkTracker;
    private final ProcessorIdempotencyCache idempotencyCache;
    private final ProcessorDlqProducer dlqProducer;
    private final ObjectMapper objectMapper;

    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong duplicateCount = new AtomicLong(0);

    public TracesProcessorConsumer(
            OpenSearchDocumentWriter openSearchWriter,
            WatermarkTracker watermarkTracker,
            ProcessorIdempotencyCache idempotencyCache,
            ProcessorDlqProducer dlqProducer,
            ObjectMapper objectMapper) {
        this.openSearchWriter = openSearchWriter;
        this.watermarkTracker = watermarkTracker;
        this.idempotencyCache = idempotencyCache;
        this.dlqProducer = dlqProducer;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${resolveiq.kafka.topics.traces:telemetry.traces}",
            groupId = "${resolveiq.kafka.consumer.traces-group:resolveiq-traces-processor}",
            containerFactory = "kafkaManualAckContainerFactory"
    )
    public void processTracesBatch(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        if (records == null || records.isEmpty()) {
            if (acknowledgment != null) acknowledgment.acknowledge();
            return;
        }

        List<TraceSpanDocument> batchToIndex = new ArrayList<>(records.size());

        for (ConsumerRecord<String, String> record : records) {
            String rawJson = record.value();
            try {
                KafkaEventEnvelope<TraceObservedPayload> envelope = objectMapper.readValue(
                        rawJson, new TypeReference<>() {});

                // 1. Idempotency Check
                if (!idempotencyCache.checkAndRegister(envelope.tenantId(), envelope.eventId())) {
                    duplicateCount.incrementAndGet();
                    log.debug("Skipping duplicate trace event {} for tenant {}", envelope.eventId(), envelope.tenantId());
                    continue;
                }

                TraceObservedPayload payload = envelope.payload();
                String serviceId = payload.serviceName() != null ? payload.serviceName() : "unknown-service";
                String streamKey = envelope.tenantId() + ":" + serviceId;
                Instant startTime = payload.startTime() != null ? payload.startTime() : envelope.timestamp();

                // 2. Watermark Update
                watermarkTracker.updateWatermark(streamKey, startTime);

                // 3. Document Normalization matching OpenSearch traces index template
                TraceSpanDocument spanDoc = new TraceSpanDocument(
                        envelope.eventId(),
                        envelope.tenantId(),
                        envelope.projectId(),
                        serviceId,
                        envelope.environment(),
                        payload.traceId(),
                        payload.spanId(),
                        payload.parentSpanId(),
                        payload.operationName(),
                        startTime,
                        payload.durationMs() != null ? payload.durationMs() : 0L,
                        payload.status() != null ? payload.status() : "OK",
                        payload.attributes(),
                        payload.resourceAttributes()
                );
                batchToIndex.add(spanDoc);

            } catch (Exception e) {
                log.error("Failed to parse trace envelope from partition {} offset {}. Routing to DLQ.",
                        record.partition(), record.offset(), e);
                dlqProducer.sendToDlq(
                        "telemetry.traces.dlq",
                        record.key(),
                        rawJson,
                        "JSON/Envelope deserialization error: " + e.getMessage(),
                        record.topic(),
                        record.partition(),
                        record.offset()
                );
            }
        }

        // 4. Bulk Index into OpenSearch with Tenant Routing
        if (!batchToIndex.isEmpty()) {
            try {
                int indexed = openSearchWriter.indexSpans(batchToIndex);
                processedCount.addAndGet(indexed);
                log.debug("Bulk indexed {} trace span records into OpenSearch", indexed);
            } catch (Exception osEx) {
                log.error("CRITICAL: OpenSearch bulk indexing failed. Backpressure engaged.", osEx);
                throw new RuntimeException("OpenSearch storage failure during traces bulk indexing", osEx);
            }
        }

        // 5. Commit Kafka Offsets ONLY after successful bulk write
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

    public void resetCounts() {
        processedCount.set(0);
        duplicateCount.set(0);
    }
}
