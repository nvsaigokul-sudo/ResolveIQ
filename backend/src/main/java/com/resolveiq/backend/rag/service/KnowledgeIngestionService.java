package com.resolveiq.backend.rag.service;

import com.resolveiq.backend.domain.KnowledgeChunkEntity;
import com.resolveiq.backend.domain.KnowledgeDocEntity;
import com.resolveiq.backend.rag.embedding.EmbeddingService;
import com.resolveiq.backend.rag.ingestion.DocumentParser;
import com.resolveiq.backend.rag.ingestion.TokenBoundedChunker;
import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.backend.repository.KnowledgeChunkRepository;
import com.resolveiq.backend.repository.KnowledgeDocRepository;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrates knowledge document ingestion, chunking, embedding, and persistence per PRD §22, §25.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);

    private final KnowledgeDocRepository docRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final DocumentParser documentParser;
    private final TokenBoundedChunker chunker;
    private final EmbeddingService embeddingService;
    private final KnowledgeSanitizer sanitizer;

    public KnowledgeIngestionService(
            KnowledgeDocRepository docRepository,
            KnowledgeChunkRepository chunkRepository,
            DocumentParser documentParser,
            TokenBoundedChunker chunker,
            EmbeddingService embeddingService,
            KnowledgeSanitizer sanitizer) {
        this.docRepository = docRepository;
        this.chunkRepository = chunkRepository;
        this.documentParser = documentParser;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.sanitizer = sanitizer;
    }

    /**
     * Ingests, parses, chunks, embeds, and saves a knowledge document for a tenant.
     */
    @Transactional
    public KnowledgeDocEntity ingestDocument(UUID tenantId, UUID projectId, KnowledgeDocType docType,
                                             String title, String sourceUri, String service,
                                             String environment, String rawContent, String metadataJson) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(docType, "docType cannot be null");
        Objects.requireNonNull(rawContent, "rawContent cannot be null");

        String safeTitle = (title != null && !title.isBlank()) ? title.trim() : "Untitled Document";
        String contentHash = computeSha256(rawContent);

        // 1. Check for duplicate content within tenant
        Optional<KnowledgeDocEntity> existingOpt = docRepository.findByTenantIdAndContentHash(tenantId, contentHash);
        if (existingOpt.isPresent()) {
            KnowledgeDocEntity existing = existingOpt.get();
            log.info("Found existing identical document id={} for tenant={}, updating timestamp", existing.getId(), tenantId);
            existing.setTitle(safeTitle);
            existing.setService(service);
            existing.setEnvironment(environment);
            existing.setSourceUri(sourceUri);
            existing.setMetadata(metadataJson);
            existing.setUpdatedAt(Instant.now());
            return docRepository.save(existing);
        }

        // 2. Parse document hierarchy
        DocumentParser.ParsedDocument parsedDoc = documentParser.parse(rawContent, safeTitle);
        if (safeTitle.equals("Untitled Document") && !parsedDoc.getInferredTitle().equals("Untitled Document")) {
            safeTitle = parsedDoc.getInferredTitle();
        }

        // 3. Persist doc record
        KnowledgeDocEntity doc = new KnowledgeDocEntity(
                tenantId, projectId, docType, safeTitle, sourceUri, service, environment,
                rawContent, contentHash, metadataJson
        );
        doc = docRepository.save(doc);

        // 4. Chunk into token-bounded sections
        List<TokenBoundedChunker.ChunkItem> chunkItems = chunker.chunk(safeTitle, parsedDoc);
        if (chunkItems.isEmpty()) {
            // Document has minimal text; create single fallback chunk
            String sanitized = sanitizer.sanitize(rawContent);
            chunkItems = List.of(new TokenBoundedChunker.ChunkItem(0, sanitized, TokenBoundedChunker.estimateTokens(sanitized), "Root"));
        }

        // 5. Generate embeddings in batch
        List<String> chunkTextsToEmbed = new ArrayList<>(chunkItems.size());
        for (TokenBoundedChunker.ChunkItem item : chunkItems) {
            chunkTextsToEmbed.add(item.getText());
        }
        List<float[]> embeddings = embeddingService.embedBatch(chunkTextsToEmbed);

        // 6. Persist chunks
        List<KnowledgeChunkEntity> chunkEntities = new ArrayList<>(chunkItems.size());
        for (int i = 0; i < chunkItems.size(); i++) {
            TokenBoundedChunker.ChunkItem item = chunkItems.get(i);
            String sanitizedText = sanitizer.sanitize(item.getText());
            float[] vector = (i < embeddings.size()) ? embeddings.get(i) : new float[embeddingService.getDimension()];

            KnowledgeChunkEntity chunk = new KnowledgeChunkEntity(
                    tenantId, doc.getId(), item.getIndex(), sanitizedText, item.getTokenCount(), null, item.getHeaderPath()
            );
            chunk.setEmbeddingVector(vector);
            chunkEntities.add(chunk);
        }

        chunkRepository.saveAll(chunkEntities);
        log.info("Successfully ingested knowledge doc id={} with {} chunks for tenant={}", doc.getId(), chunkEntities.size(), tenantId);
        return doc;
    }

    /**
     * Deletes a knowledge document and its associated chunks.
     */
    @Transactional
    public boolean deleteDocument(UUID tenantId, UUID docId) {
        Optional<KnowledgeDocEntity> docOpt = docRepository.findByIdAndTenantId(docId, tenantId);
        if (docOpt.isEmpty()) {
            return false;
        }
        chunkRepository.deleteByTenantIdAndDocId(tenantId, docId);
        docRepository.delete(docOpt.get());
        log.info("Deleted knowledge doc id={} and all associated chunks for tenant={}", docId, tenantId);
        return true;
    }

    private String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
