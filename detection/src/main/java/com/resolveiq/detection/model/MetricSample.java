package com.resolveiq.detection.model;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Single metric sample used for windowed detector evaluation.
 */
public record MetricSample(
        Instant timestamp,
        double value,
        Map<String, String> dimensions
) {
    public MetricSample {
        if (dimensions == null) dimensions = Collections.emptyMap();
        if (timestamp == null) timestamp = Instant.now();
    }
}
