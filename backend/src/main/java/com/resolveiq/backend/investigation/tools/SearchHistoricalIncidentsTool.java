package com.resolveiq.backend.investigation.tools;

import com.resolveiq.backend.rag.retrieval.HybridRetrievalEngine;
import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import com.resolveiq.common.knowledge.KnowledgeSearchRequestDto;
import com.resolveiq.common.knowledge.KnowledgeSearchResultDto;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SearchHistoricalIncidentsTool implements InvestigationTool {

    private final HybridRetrievalEngine retrievalEngine;
    private final KnowledgeSanitizer sanitizer;

    public SearchHistoricalIncidentsTool(HybridRetrievalEngine retrievalEngine,
                                         KnowledgeSanitizer sanitizer) {
        this.retrievalEngine = retrievalEngine;
        this.sanitizer = sanitizer;
    }

    @Override
    public String getName() {
        return "searchHistoricalIncidents";
    }

    @Override
    public String getDescription() {
        return "Search past resolved incidents, postmortems, and incident retrospectives via tenant-isolated hybrid RAG.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Semantic or keyword search query (e.g. database connection pool exhausted)"),
                        "limit", Map.of("type", "integer", "description", "Maximum results to return (1-5, default 3)")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public ToolExecutionResult execute(UUID tenantId, UUID incidentId, Map<String, Object> arguments) {
        String rawQuery = (String) arguments.get("query");
        if (rawQuery == null || rawQuery.isBlank()) {
            return ToolExecutionResult.failure(getName(), "Missing required parameter 'query'");
        }
        String query = sanitizer.sanitizeSnippet(rawQuery);
        int limit = 3;
        if (arguments.get("limit") != null) {
            try {
                limit = Math.min(5, Math.max(1, Integer.parseInt(arguments.get("limit").toString())));
            } catch (Exception ignored) {
            }
        }

        KnowledgeSearchRequestDto request = new KnowledgeSearchRequestDto(
                query,
                List.of(KnowledgeDocType.HISTORICAL_INCIDENT, KnowledgeDocType.POSTMORTEM),
                null,
                limit
        );
        request.setMinScore(0.20);
        request.setAlphaWeight(0.65);

        List<KnowledgeSearchResultDto> searchResults = retrievalEngine.search(tenantId, request);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<rag_knowledge domain=\"historical_incidents\" count=\"%d\">\n", searchResults.size()));
        for (KnowledgeSearchResultDto res : searchResults) {
            sb.append(res.getInertXmlRepresentation()).append("\n");
        }
        sb.append("</rag_knowledge>");

        return ToolExecutionResult.success(getName(), searchResults, sb.toString(), Collections.emptyList());
    }
}
