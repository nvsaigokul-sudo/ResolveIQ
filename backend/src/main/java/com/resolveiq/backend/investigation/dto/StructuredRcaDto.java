package com.resolveiq.backend.investigation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Machine-readable and human-readable Structured Root Cause Analysis output (PRD §24).
 */
public record StructuredRcaDto(
        UUID incidentId,
        String summary,
        IncidentImpactDto impact,
        List<TimelineMilestoneDto> timeline,
        List<RootCauseCandidateDto> candidates,
        List<String> contributingFactors,
        List<RemediationActionDto> recommendedActions,
        List<RunbookCitationDto> relevantRunbooks,
        String uncertaintyStatement,
        boolean insufficientEvidence
) {
}
