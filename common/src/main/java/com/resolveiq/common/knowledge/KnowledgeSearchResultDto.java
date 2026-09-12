package com.resolveiq.common.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * Result item returned by hybrid RAG retrieval per PRD §22, §25.
 */
public class KnowledgeSearchResultDto {

    private UUID chunkId;
    private UUID docId;
    private UUID tenantId;
    private KnowledgeDocType docType;
    private String docTitle;
    private String sourceUri;
    private String service;
    private int chunkIndex;
    private String headerPath;
    private String chunkText;
    private double compositeScore;
    private double vectorScore;
    private double bm25Score;
    private String inertXmlRepresentation;
    private Instant createdAt;

    public KnowledgeSearchResultDto() {
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public UUID getDocId() {
        return docId;
    }

    public void setDocId(UUID docId) {
        this.docId = docId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public KnowledgeDocType getDocType() {
        return docType;
    }

    public void setDocType(KnowledgeDocType docType) {
        this.docType = docType;
    }

    public String getDocTitle() {
        return docTitle;
    }

    public void setDocTitle(String docTitle) {
        this.docTitle = docTitle;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getHeaderPath() {
        return headerPath;
    }

    public void setHeaderPath(String headerPath) {
        this.headerPath = headerPath;
    }

    public String getChunkText() {
        return chunkText;
    }

    public void setChunkText(String chunkText) {
        this.chunkText = chunkText;
    }

    public double getCompositeScore() {
        return compositeScore;
    }

    public void setCompositeScore(double compositeScore) {
        this.compositeScore = compositeScore;
    }

    public double getVectorScore() {
        return vectorScore;
    }

    public void setVectorScore(double vectorScore) {
        this.vectorScore = vectorScore;
    }

    public double getBm25Score() {
        return bm25Score;
    }

    public void setBm25Score(double bm25Score) {
        this.bm25Score = bm25Score;
    }

    public String getInertXmlRepresentation() {
        return inertXmlRepresentation;
    }

    public void setInertXmlRepresentation(String inertXmlRepresentation) {
        this.inertXmlRepresentation = inertXmlRepresentation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
