package com.resolveiq.backend.rag.retrieval;

import com.resolveiq.backend.domain.KnowledgeChunkEntity;
import com.resolveiq.backend.domain.KnowledgeDocEntity;
import com.resolveiq.backend.rag.embedding.EmbeddingService;
import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.backend.repository.KnowledgeChunkRepository;
import com.resolveiq.backend.repository.KnowledgeDocRepository;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import com.resolveiq.common.knowledge.KnowledgeSearchRequestDto;
import com.resolveiq.common.knowledge.KnowledgeSearchResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid retrieval engine combining Okapi BM25 and dense vector similarity per PRD §22, §25.
 * Enforces structural tenant isolation: candidate filtering occurs BEFORE similarity ranking.
 */
@Service
public class HybridRetrievalEngine {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalEngine.class);

    private final KnowledgeDocRepository docRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final BM25Ranker bm25Ranker;
    private final VectorSimilarityRanker vectorRanker;
    private final KnowledgeSanitizer sanitizer;

    public HybridRetrievalEngine(
            KnowledgeDocRepository docRepository,
            KnowledgeChunkRepository chunkRepository,
            EmbeddingService embeddingService,
            BM25Ranker bm25Ranker,
            VectorSimilarityRanker vectorRanker,
            KnowledgeSanitizer sanitizer) {
        this.docRepository = docRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.bm25Ranker = bm25Ranker;
        this.vectorRanker = vectorRanker;
        this.sanitizer = sanitizer;
    }

    /**
     * Executes hybrid search strictly scoped to the caller's tenantId.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeSearchResultDto> search(UUID tenantId, KnowledgeSearchRequestDto request) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null for hybrid search");
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return Collections.emptyList();
        }

        String query = request.getQuery().trim();
        double alpha = (request.getAlphaWeight() != null) ? Math.max(0.0, Math.min(1.0, request.getAlphaWeight())) : 0.65;
        int limit = request.getLimit() > 0 ? Math.min(request.getLimit(), 50) : 5;
        double minScore = request.getMinScore();

        // 1. STRUCTURAL TENANT FILTERING BEFORE RANKING
        // Fetch candidate documents belonging strictly to the tenant
        List<KnowledgeDocEntity> tenantDocs = docRepository.findByTenantId(tenantId);
        if (tenantDocs.isEmpty()) {
            return Collections.emptyList();
        }

        // Apply optional metadata filters (docType, service)
        Map<UUID, KnowledgeDocEntity> docMap = new HashMap<>();
        for (KnowledgeDocEntity doc : tenantDocs) {
            if (request.getDocTypes() != null && !request.getDocTypes().isEmpty()) {
                if (!request.getDocTypes().contains(doc.getDocType())) {
                    continue;
                }
            }
            if (request.getService() != null && !request.getService().isBlank()) {
                if (doc.getService() != null && !doc.getService().equalsIgnoreCase(request.getService())) {
                    continue;
                }
            }
            docMap.put(doc.getId(), doc);
        }

        if (docMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Fetch candidate chunks strictly for this tenant and eligible docs
        List<KnowledgeChunkEntity> candidateChunks = chunkRepository.findByTenantIdAndDocIdIn(tenantId, docMap.keySet());
        if (candidateChunks.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. BM25 Lexical Scoring
        Map<KnowledgeChunkEntity, Double> bm25Scores = bm25Ranker.score(
                query,
                candidateChunks,
                KnowledgeChunkEntity::getChunkText
        );

        // 4. Dense Vector Semantic Scoring
        float[] queryEmbedding = embeddingService.embed(query);
        Map<KnowledgeChunkEntity, Double> vectorScores = vectorRanker.score(
                queryEmbedding,
                candidateChunks,
                KnowledgeChunkEntity::getEmbeddingVector
        );

        // 5. Combine and Rank
        List<KnowledgeSearchResultDto> results = new ArrayList<>(candidateChunks.size());
        for (KnowledgeChunkEntity chunk : candidateChunks) {
            double bm25 = bm25Scores.getOrDefault(chunk, 0.0);
            double vector = vectorScores.getOrDefault(chunk, 0.0);
            double composite = (alpha * vector) + ((1.0 - alpha) * bm25);

            if (composite >= minScore) {
                KnowledgeDocEntity parentDoc = docMap.get(chunk.getDocId());
                KnowledgeSearchResultDto dto = new KnowledgeSearchResultDto();
                dto.setChunkId(chunk.getId());
                dto.setDocId(chunk.getDocId());
                dto.setTenantId(chunk.getTenantId());
                dto.setChunkIndex(chunk.getChunkIndex());
                dto.setChunkText(chunk.getChunkText());
                dto.setHeaderPath(chunk.getHeaderPath());
                dto.setCompositeScore(composite);
                dto.setVectorScore(vector);
                dto.setBm25Score(bm25);
                dto.setCreatedAt(chunk.getCreatedAt());

                if (parentDoc != null) {
                    dto.setDocType(parentDoc.getDocType());
                    dto.setDocTitle(parentDoc.getTitle());
                    dto.setSourceUri(parentDoc.getSourceUri());
                    dto.setService(parentDoc.getService());
                }

                // Encapsulate in inert XML block for prompt-injection safety
                String inertXml = sanitizer.wrapInertXml(
                        dto.getDocType(), dto.getDocTitle(), dto.getSourceUri(),
                        dto.getService(), dto.getHeaderPath(), dto.getCompositeScore(),
                        chunk.getChunkText()
                );
                dto.setInertXmlRepresentation(inertXml);

                results.add(dto);
            }
        }

        // Sort descending by composite score
        results.sort(Comparator.comparingDouble(KnowledgeSearchResultDto::getCompositeScore).reversed());

        if (results.size() > limit) {
            return results.subList(0, limit);
        }
        return results;
    }
}
