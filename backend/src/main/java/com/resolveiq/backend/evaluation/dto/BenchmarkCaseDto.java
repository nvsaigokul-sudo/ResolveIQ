package com.resolveiq.backend.evaluation.dto;

import java.util.List;

public record BenchmarkCaseDto(
        String scenarioId,
        String name,
        String category,
        String rootService,
        List<String> affectedServices,
        String incidentTitle,
        String incidentSummary,
        String initialContext,
        boolean expectIncident,
        boolean expectInsufficientEvidence,
        List<String> expectedEvidenceKeywords,
        List<String> expectedHypothesesKeywords,
        double minConfidence,
        boolean isAdversarial,
        String adversarialPayload
) {
}
