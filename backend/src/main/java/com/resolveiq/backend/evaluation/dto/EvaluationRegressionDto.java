package com.resolveiq.backend.evaluation.dto;

import java.util.List;
import java.util.UUID;

public record EvaluationRegressionDto(
        UUID currentRunId,
        UUID baselineRunId,
        boolean hasRegression,
        double top1Delta,
        double top3Delta,
        double groundingDelta,
        double hallucinationDelta,
        double insufficientEvidenceDelta,
        long latencyDeltaMs,
        List<String> regressionReasons
) {
}
