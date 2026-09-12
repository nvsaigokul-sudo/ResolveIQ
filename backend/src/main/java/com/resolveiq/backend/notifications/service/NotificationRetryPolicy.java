package com.resolveiq.backend.notifications.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with jitter for notification retries (PRD §27).
 */
@Component
public class NotificationRetryPolicy {

    private final long initialBackoffMs;
    private final double multiplier;
    private final long maxBackoffMs;

    public NotificationRetryPolicy() {
        this(1000L, 2.0, 60000L);
    }

    public NotificationRetryPolicy(long initialBackoffMs, double multiplier, long maxBackoffMs) {
        this.initialBackoffMs = initialBackoffMs;
        this.multiplier = multiplier;
        this.maxBackoffMs = maxBackoffMs;
    }

    public Duration calculateBackoff(int attemptCount) {
        if (attemptCount <= 0) {
            return Duration.ofMillis(initialBackoffMs);
        }
        double rawBackoff = initialBackoffMs * Math.pow(multiplier, attemptCount - 1);
        long backoff = Math.min((long) rawBackoff, maxBackoffMs);
        long jitter = ThreadLocalRandom.current().nextLong(50, 300);
        return Duration.ofMillis(backoff + jitter);
    }
}
