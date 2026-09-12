package com.resolveiq.backend.evaluation.dto;

import java.time.Instant;
import java.util.UUID;

public record EvaluationCaseResultDto(
        UUID id,
        UUID runId,
        String scenarioId,
        String scenarioCategory,
        String status,
        String expectedService,
        String predictedService,
        boolean top1Match,
        boolean top3Match,
        double confidence,
        boolean evidenceGrounded,
        boolean hallucinationDetected,
        boolean insufficientEvidenceCorrect,
        int toolCalls,
        long durationMs,
        int tokensUsed,
        String failureReason,
        String metricsJson,
        Instant createdAt
) {
}
