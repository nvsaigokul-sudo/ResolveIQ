package com.resolveiq.ingestion.ratelimit;

import com.resolveiq.common.exception.ResolveIQException;

import java.util.Map;

/**
 * Thrown when a tenant's token-bucket rate limit is exceeded (PRD §11.1(7), §15.1).
 * Triggers HTTP 429 Too Many Requests with Retry-After header.
 */
public class TenantRateLimitExceededException extends ResolveIQException {

    private final long retryAfterSeconds;

    public TenantRateLimitExceededException(String message, long retryAfterSeconds) {
        super("TENANT_RATE_LIMIT_EXCEEDED", message, Map.of("retryAfterSeconds", retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
