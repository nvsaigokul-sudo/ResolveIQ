package com.resolveiq.backend.evaluation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EvaluationReportDto(
        UUID runId,
        UUID tenantId,
        String modelIdentifier,
        String benchmarkSuite,
        int totalCases,
        int passedCases,
        double top1Accuracy,
        double top3Accuracy,
        double evidencePrecision,
        double evidenceGrounding,
        double hallucinationRate,
        double insufficientEvidenceAccuracy,
        double completionRate,
        double avgToolCalls,
        long p50DurationMs,
        long p95DurationMs,
        int totalTokens,
        double estimatedCostUsd,
        boolean regressionDetected,
        Instant createdAt,
        List<EvaluationCaseResultDto> caseResults
) {
}
