package com.resolveiq.processors.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Dead-Letter Queue (DLQ) producer for unrecoverable consumer failures (PRD §4.4, §51).
 * Preserves failed message body, failure reason, exception stack trace, and original offset coordinates.
 */
@Component
public class ProcessorDlqProducer {

    private static final Logger log = LoggerFactory.getLogger(ProcessorDlqProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedQueue<Map<String, Object>> inMemoryDlqStore = new ConcurrentLinkedQueue<>();

    public ProcessorDlqProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Routes a failed message to the appropriate DLQ topic.
     *
     * @param dlqTopic       the target dead-letter queue topic (e.g. telemetry.metrics.dlq)
     * @param partitionKey   original partition key (tenant_id:service_id)
     * @param originalPayload original raw payload or serialized envelope
     * @param failureReason  diagnostic error explanation
     * @param originalTopic  source topic where failure occurred
     * @param partition      source partition
     * @param offset         source offset
     */
    public void sendToDlq(String dlqTopic, String partitionKey, String originalPayload, String failureReason,
                          String originalTopic, int partition, long offset) {
        Map<String, Object> dlqEntry = new LinkedHashMap<>();
        dlqEntry.put("dlq_topic", dlqTopic);
        dlqEntry.put("original_topic", originalTopic);
        dlqEntry.put("original_partition", partition);
        dlqEntry.put("original_offset", offset);
        dlqEntry.put("partition_key", partitionKey);
        dlqEntry.put("failure_reason", failureReason);
        dlqEntry.put("failed_at", Instant.now().toString());
        dlqEntry.put("payload", originalPayload);

        inMemoryDlqStore.add(dlqEntry);

        try {
            String dlqJson = objectMapper.writeValueAsString(dlqEntry);
            ProducerRecord<String, String> record = new ProducerRecord<>(dlqTopic, partitionKey, dlqJson);
            record.headers().add(new RecordHeader("X-DLQ-Original-Topic", originalTopic.getBytes(StandardCharsets.UTF_8)));
            record.headers().add(new RecordHeader("X-DLQ-Failure-Reason", failureReason.getBytes(StandardCharsets.UTF_8)));

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("CRITICAL: Failed to publish message to DLQ topic {}", dlqTopic, ex);
                } else {
                    log.info("Routed unrecoverable message to DLQ topic {} at offset {}",
                            dlqTopic, result.getRecordMetadata().offset());
                }
            });
        } catch (Exception e) {
            log.error("Failed to serialize DLQ entry for topic {}", dlqTopic, e);
        }
    }

    public ConcurrentLinkedQueue<Map<String, Object>> getInMemoryDlqStore() {
        return inMemoryDlqStore;
    }

    public void clear() {
        inMemoryDlqStore.clear();
    }
}
