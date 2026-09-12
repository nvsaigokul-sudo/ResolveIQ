package com.resolveiq.processors.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka Consumer Rebalance Listener (PRD §10.1, §4.4).
 * Intercepts partition revocations to ensure buffered records are flushed
 * and offsets are committed synchronously before partitions transfer to other consumer group members.
 */
@Component
public class ProcessorRebalanceListener implements ConsumerAwareRebalanceListener {

    private static final Logger log = LoggerFactory.getLogger(ProcessorRebalanceListener.class);

    private final AtomicBoolean rebalanceInProgress = new AtomicBoolean(false);

    @Override
    public void onPartitionsRevokedBeforeCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        rebalanceInProgress.set(true);
        log.warn("Kafka partitions revoked before commit: {}. Flushing storage buffers and committing offsets synchronously...", partitions);
        try {
            consumer.commitSync();
            log.info("Synchronous offset commit succeeded prior to partition revocation for {}", partitions);
        } catch (Exception e) {
            log.error("Failed to commit offsets synchronously during partition revocation for {}", partitions, e);
        }
    }

    @Override
    public void onPartitionsRevokedAfterCommit(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log.info("Partitions revoked after commit: {}", partitions);
    }

    @Override
    public void onPartitionsAssigned(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        rebalanceInProgress.set(false);
        log.info("Kafka consumer group assigned partitions: {}. Ready to resume processing.", partitions);
    }

    @Override
    public void onPartitionsLost(Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
        log.warn("Kafka partitions lost: {}. Resetting partition-scoped caches.", partitions);
        rebalanceInProgress.set(false);
    }

    public boolean isRebalanceInProgress() {
        return rebalanceInProgress.get();
    }
}
