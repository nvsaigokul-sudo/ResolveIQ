package com.resolveiq.backend.notifications.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tenant token-bucket rate limiter for notification dispatches (PRD §52).
 * Protects downstream providers from alert flooding and spam.
 */
@Component
public class NotificationRateLimiter {

    private final int capacity;
    private final double refillTokensPerSecond;

    private final Map<UUID, TenantBucket> tenantBuckets = new ConcurrentHashMap<>();

    public NotificationRateLimiter() {
        this(100, 100.0 / 60.0); // 100 notifications per minute
    }

    public NotificationRateLimiter(int capacity, double refillTokensPerSecond) {
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    public synchronized boolean tryAcquire(UUID tenantId) {
        TenantBucket bucket = tenantBuckets.computeIfAbsent(tenantId, k -> new TenantBucket(capacity, Instant.now()));
        return bucket.tryConsume(1, capacity, refillTokensPerSecond);
    }

    public void reset(UUID tenantId) {
        tenantBuckets.remove(tenantId);
    }

    private static class TenantBucket {
        private double tokens;
        private Instant lastRefill;

        public TenantBucket(double tokens, Instant lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }

        public synchronized boolean tryConsume(int tokensToConsume, int maxCapacity, double refillRate) {
            refill(maxCapacity, refillRate);
            if (tokens >= tokensToConsume) {
                tokens -= tokensToConsume;
                return true;
            }
            return false;
        }

        private void refill(int maxCapacity, double refillRate) {
            Instant now = Instant.now();
            double secondsPassed = Math.max(0, (now.toEpochMilli() - lastRefill.toEpochMilli()) / 1000.0);
            tokens = Math.min(maxCapacity, tokens + (secondsPassed * refillRate));
            lastRefill = now;
        }
    }
}
