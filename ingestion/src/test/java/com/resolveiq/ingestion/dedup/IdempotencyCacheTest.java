package com.resolveiq.ingestion.dedup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyCacheTest {

    @Test
    @DisplayName("Deduplicates repeated event_ids within the deduplication window")
    void testEventDeduplication() {
        IdempotencyCache cache = new IdempotencyCache(10);
        UUID eventId = UUID.randomUUID();

        // First occurrence is unique
        assertThat(cache.recordIfUnique(eventId)).isTrue();
        assertThat(cache.isDuplicate(eventId)).isTrue();

        // Second occurrence within dedup window is detected as duplicate
        assertThat(cache.recordIfUnique(eventId)).isFalse();

        // Different event ID is unique
        UUID differentEventId = UUID.randomUUID();
        assertThat(cache.recordIfUnique(differentEventId)).isTrue();
    }
}
