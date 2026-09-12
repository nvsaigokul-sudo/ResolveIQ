package com.resolveiq.backend.investigation.loop;

import com.resolveiq.backend.investigation.dto.StructuredRcaDto;
import com.resolveiq.backend.investigation.llm.LlmMessage;
import com.resolveiq.backend.investigation.tools.DiscoveredEvidenceItem;

import java.util.List;

public record InvestigationLoopResult(
        StructuredRcaDto structuredRca,
        List<DiscoveredEvidenceItem> discoveredEvidence,
        int toolCallCount,
        int totalTokens,
        int stepCount,
        long durationMs,
        List<LlmMessage> transcript,
        boolean timedOut
) {
}
