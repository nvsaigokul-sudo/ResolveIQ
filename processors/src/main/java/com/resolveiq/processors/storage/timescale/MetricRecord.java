package com.resolveiq.processors.storage.timescale;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Normalized database record for TimescaleDB metrics_raw table (PRD §10.3, §13.2).
 */
public record MetricRecord(
        Instant time,
        UUID tenantId,
        UUID projectId,
        String serviceId,
        String environment,
        String metricName,
        String metricType,
        double metricValue,
        String unit,
        Map<String, String> dimensions,
        String dimensionsHash,
        UUID eventId,
        boolean isLate,
        Instant createdAt
) {}
