package com.resolveiq.ingestion.dedup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ingestion and processor layer idempotency cache (PRD Section 15.1).
 * Tracks producer-generated event_ids to discard duplicate events within the dedup window.
 */
@Component
public class IdempotencyCache {

    private final long ttlMillis;
    private final ConcurrentHashMap<UUID, Long> seenEvents = new ConcurrentHashMap<>();

    public IdempotencyCache(@Value("${resolveiq.ingestion.dedup.ttl-minutes:10}") int ttlMinutes) {
        this.ttlMillis = ttlMinutes * 60L * 1000L;
    }

    /**
     * Checks if the event_id is unique.
     * If unseen, records it and returns true. If duplicate, returns false.
     */
    public boolean recordIfUnique(UUID eventId) {
        if (eventId == null) {
            return true; // No ID provided, cannot dedup by ID
        }

        long now = System.currentTimeMillis();
        cleanOldEntriesIfExceeded(now);

        Long previousTimestamp = seenEvents.putIfAbsent(eventId, now);
        if (previousTimestamp == null) {
            return true; // Unique
        }

        if (now - previousTimestamp > ttlMillis) {
            seenEvents.put(eventId, now);
            return true; // Expired, treat as fresh
        }

        return false; // Duplicate within dedup window
    }

    public boolean isDuplicate(UUID eventId) {
        if (eventId == null) {
            return false;
        }
        Long timestamp = seenEvents.get(eventId);
        if (timestamp == null) {
            return false;
        }
        return (System.currentTimeMillis() - timestamp) <= ttlMillis;
    }

    private void cleanOldEntriesIfExceeded(long now) {
        if (seenEvents.size() > 50000) {
            seenEvents.entrySet().removeIf(entry -> (now - entry.getValue()) > ttlMillis);
        }
    }

    public void clear() {
        seenEvents.clear();
    }
}
