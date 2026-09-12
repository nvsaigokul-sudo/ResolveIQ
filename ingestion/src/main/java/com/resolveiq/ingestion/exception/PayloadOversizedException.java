package com.resolveiq.ingestion.exception;

import com.resolveiq.common.exception.ResolveIQException;

import java.util.Map;

/**
 * Thrown when an incoming telemetry payload exceeds max batch size (default 1MB) per PRD Section 15.1.
 */
public class PayloadOversizedException extends ResolveIQException {

    public PayloadOversizedException(String message, long sizeBytes, long maxAllowedBytes) {
        super("PAYLOAD_OVERSIZED", message, Map.of(
                "sizeBytes", sizeBytes,
                "maxAllowedBytes", maxAllowedBytes
        ));
    }
}
