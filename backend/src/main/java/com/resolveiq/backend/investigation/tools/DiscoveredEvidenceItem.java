package com.resolveiq.backend.investigation.tools;

import com.resolveiq.common.incident.EvidenceSource;

public record DiscoveredEvidenceItem(
        EvidenceSource source,
        String service,
        String dataPayload,
        String queryReference,
        double relevanceScore,
        double confidence
) {
}
