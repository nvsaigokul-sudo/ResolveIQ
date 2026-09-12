package com.resolveiq.backend.investigation.dto;

import com.resolveiq.common.incident.InvestigationStatus;

import java.time.Instant;
import java.util.UUID;

public record InvestigationResultDto(
        UUID investigationId,
        UUID incidentId,
        UUID tenantId,
        InvestigationStatus status,
        String modelIdentifier,
        int toolCallCount,
        int totalTokens,
        double estimatedCost,
        long durationMs,
        Instant startedAt,
        Instant completedAt,
        StructuredRcaDto structuredRca
) {
}
