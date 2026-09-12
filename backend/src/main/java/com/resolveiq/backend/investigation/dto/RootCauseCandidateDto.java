package com.resolveiq.backend.investigation.dto;

import java.util.List;
import java.util.UUID;

public record RootCauseCandidateDto(
        int rank,
        String hypothesis,
        String rootService,
        double confidence,
        String reasoning,
        List<UUID> supportingEvidenceIds,
        List<UUID> contradictingEvidenceIds
) {
}
