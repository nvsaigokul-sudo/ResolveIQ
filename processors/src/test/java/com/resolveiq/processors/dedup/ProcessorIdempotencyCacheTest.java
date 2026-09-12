package com.resolveiq.processors.dedup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorIdempotencyCacheTest {

    private ProcessorIdempotencyCache cache;

    @BeforeEach
    void setUp() {
        cache = new ProcessorIdempotencyCache();
    }

    @Test
    @DisplayName("First presentation of event ID is accepted; subsequent is rejected as duplicate")
    void testEventDeduplication() {
        UUID tenantId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // First presentation -> accepted
        boolean accepted = cache.checkAndRegister(tenantId, eventId);
        assertThat(accepted).isTrue();
        assertThat(cache.size()).isEqualTo(1);

        // Immediate duplicate presentation -> rejected
        boolean second = cache.checkAndRegister(tenantId, eventId);
        assertThat(second).isFalse();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Distinct tenants with same event ID are strictly isolated")
    void testTenantIsolationInDedup() {
        UUID tenantAlpha = UUID.randomUUID();
        UUID tenantBeta = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        // Tenant Alpha registers eventId
        assertThat(cache.checkAndRegister(tenantAlpha, eventId)).isTrue();

        // Tenant Beta presents same eventId -> permitted because tenant context differs
        assertThat(cache.checkAndRegister(tenantBeta, eventId)).isTrue();
        assertThat(cache.size()).isEqualTo(2);
    }
}
