package com.resolveiq.backend.evaluation.dto;

import java.util.List;
import java.util.UUID;

public record EvaluationRunRequest(
        String benchmarkSuite,
        UUID baselineRunId,
        List<String> scenarioFilter
) {
}
