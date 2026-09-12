package com.resolveiq.backend.domain;

import com.resolveiq.common.incident.InvestigationStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Investigation session associated with an incident (PRD §36.1, §36.2, §56).
 */
@Entity
@Table(name = "investigations")
public class InvestigationEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvestigationStatus status = InvestigationStatus.PENDING;

    @Column(name = "model_identifier")
    private String modelIdentifier;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "tool_call_count", nullable = false)
    private int toolCallCount = 0;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens = 0;

    @Column(name = "estimated_cost", nullable = false)
    private double estimatedCost = 0.0;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "uncertainty_statement", columnDefinition = "TEXT")
    private String uncertaintyStatement;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public InvestigationEntity() {
    }

    public InvestigationEntity(UUID tenantId, UUID incidentId) {
        super(tenantId);
        this.incidentId = incidentId;
        this.status = InvestigationStatus.PENDING;
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

    public InvestigationStatus getStatus() {
        return status;
    }

    public void setStatus(InvestigationStatus status) {
        this.status = status;
    }

    public String getModelIdentifier() {
        return modelIdentifier;
    }

    public void setModelIdentifier(String modelIdentifier) {
        this.modelIdentifier = modelIdentifier;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public void setToolCallCount(int toolCallCount) {
        this.toolCallCount = toolCallCount;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public double getEstimatedCost() {
        return estimatedCost;
    }

    public void setEstimatedCost(double estimatedCost) {
        this.estimatedCost = estimatedCost;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getUncertaintyStatement() {
        return uncertaintyStatement;
    }

    public void setUncertaintyStatement(String uncertaintyStatement) {
        this.uncertaintyStatement = uncertaintyStatement;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
