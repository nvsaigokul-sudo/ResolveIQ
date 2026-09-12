package com.resolveiq.backend.investigation.dto;

public record RemediationActionDto(
        int priority,
        String action,
        String targetService,
        String runbookReference,
        String commandSnippet
) {
}
