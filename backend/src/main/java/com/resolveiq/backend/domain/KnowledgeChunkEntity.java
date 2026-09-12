package com.resolveiq.backend.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * Token-bounded chunk with vector embedding per PRD §22, §25, §36.1.
 */
@Entity
@Table(name = "knowledge_chunks")
public class KnowledgeChunkEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "doc_id", nullable = false)
    private UUID docId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "header_path")
    private String headerPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public KnowledgeChunkEntity() {
    }

    public KnowledgeChunkEntity(UUID tenantId, UUID docId, int chunkIndex, String chunkText,
                                int tokenCount, String embedding, String headerPath) {
        super(tenantId);
        this.docId = docId;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
        this.tokenCount = tokenCount;
        this.embedding = embedding;
        this.headerPath = headerPath;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDocId() {
        return docId;
    }

    public void setDocId(UUID docId) {
        this.docId = docId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public float[] getEmbeddingVector() {
        if (embedding == null || embedding.isBlank()) {
            return new float[0];
        }
        String clean = embedding.trim();
        if (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        if (clean.isBlank()) {
            return new float[0];
        }
        String[] parts = clean.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }

    public void setEmbeddingVector(float[] vector) {
        if (vector == null || vector.length == 0) {
            this.embedding = "[]";
            return;
        }
        StringBuilder sb = new StringBuilder(vector.length * 10);
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        this.embedding = sb.toString();
    }

    public String getHeaderPath() {
        return headerPath;
    }

    public void setHeaderPath(String headerPath) {
        this.headerPath = headerPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
