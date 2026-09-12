package com.resolveiq.backend.investigation.tools;

import com.resolveiq.backend.rag.retrieval.HybridRetrievalEngine;
import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import com.resolveiq.common.knowledge.KnowledgeSearchRequestDto;
import com.resolveiq.common.knowledge.KnowledgeSearchResultDto;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SearchRunbooksTool implements InvestigationTool {

    private final HybridRetrievalEngine retrievalEngine;
    private final KnowledgeSanitizer sanitizer;

    public SearchRunbooksTool(HybridRetrievalEngine retrievalEngine,
                             KnowledgeSanitizer sanitizer) {
        this.retrievalEngine = retrievalEngine;
        this.sanitizer = sanitizer;
    }

    @Override
    public String getName() {
        return "searchRunbooks";
    }

    @Override
    public String getDescription() {
        return "Search operational runbooks, mitigation playbooks, and troubleshooting guides via tenant-isolated hybrid RAG.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Runbook topic or procedure to search for (e.g. database failover, restart pool)"),
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
                List.of(KnowledgeDocType.RUNBOOK, KnowledgeDocType.TROUBLESHOOTING),
                null,
                limit
        );
        request.setMinScore(0.20);
        request.setAlphaWeight(0.65);

        List<KnowledgeSearchResultDto> searchResults = retrievalEngine.search(tenantId, request);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<rag_knowledge domain=\"runbooks\" count=\"%d\">\n", searchResults.size()));
        for (KnowledgeSearchResultDto res : searchResults) {
            sb.append(res.getInertXmlRepresentation()).append("\n");
        }
        sb.append("</rag_knowledge>");

        return ToolExecutionResult.success(getName(), searchResults, sb.toString(), Collections.emptyList());
    }
}
