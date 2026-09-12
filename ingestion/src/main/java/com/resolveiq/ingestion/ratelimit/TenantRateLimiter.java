package com.resolveiq.ingestion.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-Bucket Rate Limiter per tenant (PRD Section 11.1(7), Section 15.1).
 * Isolates noisy neighbors and enforces plan-based ingestion limits.
 */
@Component
public class TenantRateLimiter {

    private final long defaultCapacity;
    private final double defaultRefillRatePerSecond;
    private final ConcurrentHashMap<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();

    public TenantRateLimiter(
            @Value("${resolveiq.ingestion.ratelimit.capacity:10000}") long defaultCapacity,
            @Value("${resolveiq.ingestion.ratelimit.refill-rate:5000}") double defaultRefillRatePerSecond) {
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillRatePerSecond = defaultRefillRatePerSecond;
    }

    public record RateLimitResult(
            boolean isAllowed,
            long retryAfterSeconds,
            long remainingTokens
    ) {}

    public RateLimitResult tryConsume(UUID tenantId, int tokensToConsume) {
        if (tenantId == null) {
            return new RateLimitResult(false, 1, 0);
        }

        TokenBucket bucket = buckets.computeIfAbsent(
                tenantId,
                id -> new TokenBucket(defaultCapacity, defaultRefillRatePerSecond)
        );

        return bucket.tryConsume(tokensToConsume);
    }

    public void reset(UUID tenantId) {
        buckets.remove(tenantId);
    }

    public static class TokenBucket {
        private final long capacity;
        private final double refillRatePerSecond;
        private double availableTokens;
        private long lastRefillTimestampMillis;

        public TokenBucket(long capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.availableTokens = capacity;
            this.lastRefillTimestampMillis = System.currentTimeMillis();
        }

        public synchronized RateLimitResult tryConsume(int tokens) {
            refill();

            if (availableTokens >= tokens) {
                availableTokens -= tokens;
                return new RateLimitResult(true, 0, (long) availableTokens);
            }

            double deficit = tokens - availableTokens;
            long retryAfterSeconds = Math.max(1, (long) Math.ceil(deficit / refillRatePerSecond));
            return new RateLimitResult(false, retryAfterSeconds, (long) availableTokens);
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsedMillis = now - lastRefillTimestampMillis;
            if (elapsedMillis > 0) {
                double tokensToAdd = (elapsedMillis / 1000.0) * refillRatePerSecond;
                availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
                lastRefillTimestampMillis = now;
            }
        }
    }
}
