package com.resolveiq.backend.investigation.dto;

import java.time.Instant;
import java.util.List;

public record IncidentImpactDto(
        List<String> affectedServices,
        String severity,
        Instant startTime,
        Long durationMinutes,
        String userImpactSummary
) {
}
