package com.resolveiq.backend.investigation.dto;

import java.util.UUID;

public record RunbookCitationDto(
        UUID documentId,
        String title,
        String service,
        double relevanceScore,
        String sourceUri
) {
}
