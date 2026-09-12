package com.resolveiq.common.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for hybrid knowledge search per PRD §22, §25.
 */
public class KnowledgeSearchRequestDto {

    private String query;
    private List<KnowledgeDocType> docTypes;
    private String service;
    private int limit = 5;
    private double minScore = 0.0;
    private Double alphaWeight = 0.65; // Default 0.65 vector weight, 0.35 BM25 weight

    public KnowledgeSearchRequestDto() {
    }

    public KnowledgeSearchRequestDto(String query, List<KnowledgeDocType> docTypes, String service, int limit) {
        this.query = query;
        this.docTypes = docTypes;
        this.service = service;
        this.limit = limit;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<KnowledgeDocType> getDocTypes() {
        return docTypes;
    }

    public void setDocTypes(List<KnowledgeDocType> docTypes) {
        this.docTypes = docTypes;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public Double getAlphaWeight() {
        return alphaWeight;
    }

    public void setAlphaWeight(Double alphaWeight) {
        this.alphaWeight = alphaWeight;
    }
}
