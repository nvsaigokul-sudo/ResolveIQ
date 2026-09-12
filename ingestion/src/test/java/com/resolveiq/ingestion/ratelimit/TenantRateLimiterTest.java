package com.resolveiq.ingestion.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRateLimiterTest {

    @Test
    @DisplayName("Allows consumption within capacity and rejects bursts exceeding capacity with Retry-After")
    void testTokenBucketConsumptionAndRejection() {
        // Capacity = 5 tokens, refill rate = 1 token/sec
        TenantRateLimiter rateLimiter = new TenantRateLimiter(5, 1.0);
        UUID tenantId = UUID.randomUUID();

        // 1. Consume 3 tokens (Allowed)
        TenantRateLimiter.RateLimitResult res1 = rateLimiter.tryConsume(tenantId, 3);
        assertThat(res1.isAllowed()).isTrue();
        assertThat(res1.remainingTokens()).isEqualTo(2);

        // 2. Consume 2 tokens (Allowed, bucket now empty)
        TenantRateLimiter.RateLimitResult res2 = rateLimiter.tryConsume(tenantId, 2);
        assertThat(res2.isAllowed()).isTrue();
        assertThat(res2.remainingTokens()).isEqualTo(0);

        // 3. Attempt to consume 1 token (Exhausted, Rejected)
        TenantRateLimiter.RateLimitResult res3 = rateLimiter.tryConsume(tenantId, 1);
        assertThat(res3.isAllowed()).isFalse();
        assertThat(res3.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Guarantees noisy neighbor isolation between Tenant A and Tenant B")
    void testNoisyNeighborIsolation() {
        TenantRateLimiter rateLimiter = new TenantRateLimiter(5, 1.0);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // Tenant A exhausts all tokens
        TenantRateLimiter.RateLimitResult resA1 = rateLimiter.tryConsume(tenantA, 5);
        assertThat(resA1.isAllowed()).isTrue();

        TenantRateLimiter.RateLimitResult resA2 = rateLimiter.tryConsume(tenantA, 1);
        assertThat(resA2.isAllowed()).isFalse(); // Tenant A blocked

        // Tenant B is completely unaffected and consumes successfully
        TenantRateLimiter.RateLimitResult resB1 = rateLimiter.tryConsume(tenantB, 4);
        assertThat(resB1.isAllowed()).isTrue();
        assertThat(resB1.remainingTokens()).isEqualTo(1);
    }
}
