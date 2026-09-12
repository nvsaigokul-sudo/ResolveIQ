package com.resolveiq.backend.investigation.dto;

import java.time.Instant;

public record TimelineMilestoneDto(
        Instant timestamp,
        String phase,
        String description
) {
}
