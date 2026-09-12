package com.resolveiq.backend.investigation.tools;

import java.util.Collections;
import java.util.List;

public record ToolExecutionResult(
        boolean success,
        String toolName,
        Object data,
        String formattedResult,
        List<DiscoveredEvidenceItem> discoveredEvidence,
        String errorMessage
) {
    public static ToolExecutionResult success(String toolName, Object data, String formattedResult, List<DiscoveredEvidenceItem> discoveredEvidence) {
        return new ToolExecutionResult(true, toolName, data, formattedResult,
                discoveredEvidence != null ? discoveredEvidence : Collections.emptyList(), null);
    }

    public static ToolExecutionResult failure(String toolName, String errorMessage) {
        return new ToolExecutionResult(false, toolName, null,
                "<error tool=\"" + toolName + "\">" + errorMessage + "</error>",
                Collections.emptyList(), errorMessage);
    }
}
