package com.resolveiq.ingestion.kafka;

import com.resolveiq.common.exception.ResolveIQException;

import java.util.Map;

/**
 * Thrown when Kafka produce exceeds latency threshold or broker is unavailable (PRD §15.1, §31).
 * Triggers HTTP 503 with Retry-After header.
 */
public class IngestionBackpressureException extends ResolveIQException {

    private final long retryAfterSeconds;

    public IngestionBackpressureException(String message, long retryAfterSeconds) {
        super("INGESTION_BACKPRESSURE", message, Map.of("retryAfterSeconds", retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
