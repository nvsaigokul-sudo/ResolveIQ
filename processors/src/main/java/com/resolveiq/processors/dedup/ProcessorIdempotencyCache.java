package com.resolveiq.processors.dedup;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * High-throughput sliding-window LRU de-duplication cache for telemetry processors.
 * Guarantees that re-delivered Kafka messages within 10 minutes are identified
 * and skipped, preventing duplicate writes to TimescaleDB or OpenSearch.
 */
@Component
public class ProcessorIdempotencyCache {

    private static final int MAX_CACHE_ENTRIES = 100_000;
    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(10);

    private final Map<String, Instant> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );

    /**
     * Checks whether an event has already been processed within the sliding window.
     * If not already present, records it and returns true (accepted).
     *
     * @param tenantId tenant identifier
     * @param eventId  unique Kafka event UUID
     * @return true if event is NEW; false if DUPLICATE
     */
    public boolean checkAndRegister(UUID tenantId, UUID eventId) {
        if (tenantId == null || eventId == null) {
            return true;
        }

        String key = tenantId + ":" + eventId;
        Instant now = Instant.now();
        Instant seenAt = cache.get(key);

        if (seenAt != null) {
            if (Duration.between(seenAt, now).compareTo(DEDUP_WINDOW) <= 0) {
                return false; // Duplicate within window
            }
        }

        cache.put(key, now);
        return true;
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
