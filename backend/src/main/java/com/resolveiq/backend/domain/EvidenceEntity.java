package com.resolveiq.backend.domain;

import com.resolveiq.common.incident.EvidenceSource;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted evidence record associated with an incident (PRD §21.2).
 * Every evidence row is persisted (not ephemeral) so an investigation's evidence trail
 * can be audited and re-examined after the fact.
 */
@Entity
@Table(name = "evidence")
public class EvidenceEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceSource source;

    @Column(nullable = false)
    private String service;

    @Column(name = "query_used", nullable = false)
    private String queryUsed;

    @Column(name = "result_reference", nullable = false)
    private String resultReference;

    @Column(name = "payload_summary", nullable = false, columnDefinition = "TEXT")
    private String payloadSummary;

    @Column(name = "relevance_score", nullable = false)
    private Double relevanceScore = 1.0;

    @Column(nullable = false)
    private Double confidence = 1.0;

    @Column(name = "relationship_to_hypothesis")
    private String relationshipToHypothesis;

    @Column(nullable = false)
    private Instant timestamp = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EvidenceEntity() {
    }

    public EvidenceEntity(UUID tenantId, UUID incidentId, EvidenceSource source, String service,
                          String queryUsed, String resultReference, String payloadSummary,
                          Double relevanceScore, Double confidence, String relationshipToHypothesis,
                          Instant timestamp) {
        super(tenantId);
        this.incidentId = incidentId;
        this.source = source;
        this.service = service;
        this.queryUsed = queryUsed;
        this.resultReference = resultReference;
        this.payloadSummary = payloadSummary;
        this.relevanceScore = relevanceScore != null ? relevanceScore : 1.0;
        this.confidence = confidence != null ? confidence : 1.0;
        this.relationshipToHypothesis = relationshipToHypothesis;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(UUID incidentId) {
        this.incidentId = incidentId;
    }

    public EvidenceSource getSource() {
        return source;
    }

    public void setSource(EvidenceSource source) {
        this.source = source;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getQueryUsed() {
        return queryUsed;
    }

    public void setQueryUsed(String queryUsed) {
        this.queryUsed = queryUsed;
    }

    public String getResultReference() {
        return resultReference;
    }

    public void setResultReference(String resultReference) {
        this.resultReference = resultReference;
    }

    public String getPayloadSummary() {
        return payloadSummary;
    }

    public void setPayloadSummary(String payloadSummary) {
        this.payloadSummary = payloadSummary;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getRelationshipToHypothesis() {
        return relationshipToHypothesis;
    }

    public void setRelationshipToHypothesis(String relationshipToHypothesis) {
        this.relationshipToHypothesis = relationshipToHypothesis;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
