package com.resolveiq.backend.evaluation.domain;

import com.resolveiq.backend.domain.TenantScopedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted result of an individual evaluation scenario within an evaluation run (PRD §30, §56).
 */
@Entity
@Table(name = "evaluation_case_results")
public class EvaluationCaseResultEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "scenario_id", nullable = false)
    private String scenarioId;

    @Column(name = "scenario_category", nullable = false)
    private String scenarioCategory;

    @Column(nullable = false)
    private String status = "PASSED";

    @Column(name = "expected_service")
    private String expectedService;

    @Column(name = "predicted_service")
    private String predictedService;

    @Column(name = "top1_match", nullable = false)
    private boolean top1Match;

    @Column(name = "top3_match", nullable = false)
    private boolean top3Match;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "evidence_grounded", nullable = false)
    private boolean evidenceGrounded;

    @Column(name = "hallucination_detected", nullable = false)
    private boolean hallucinationDetected;

    @Column(name = "insufficient_evidence_correct", nullable = false)
    private boolean insufficientEvidenceCorrect;

    @Column(name = "tool_calls", nullable = false)
    private int toolCalls;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "tokens_used", nullable = false)
    private int tokensUsed;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "metrics_json", columnDefinition = "TEXT")
    private String metricsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public EvaluationCaseResultEntity() {}

    public EvaluationCaseResultEntity(
            UUID tenantId,
            UUID runId,
            String scenarioId,
            String scenarioCategory,
            String status,
            String expectedService,
            String predictedService,
            boolean top1Match,
            boolean top3Match,
            double confidence,
            boolean evidenceGrounded,
            boolean hallucinationDetected,
            boolean insufficientEvidenceCorrect,
            int toolCalls,
            long durationMs,
            int tokensUsed,
            String failureReason,
            String metricsJson) {
        super(tenantId);
        this.runId = runId;
        this.scenarioId = scenarioId;
        this.scenarioCategory = scenarioCategory;
        this.status = status;
        this.expectedService = expectedService;
        this.predictedService = predictedService;
        this.top1Match = top1Match;
        this.top3Match = top3Match;
        this.confidence = confidence;
        this.evidenceGrounded = evidenceGrounded;
        this.hallucinationDetected = hallucinationDetected;
        this.insufficientEvidenceCorrect = insufficientEvidenceCorrect;
        this.toolCalls = toolCalls;
        this.durationMs = durationMs;
        this.tokensUsed = tokensUsed;
        this.failureReason = failureReason;
        this.metricsJson = metricsJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getScenarioCategory() {
        return scenarioCategory;
    }

    public void setScenarioCategory(String scenarioCategory) {
        this.scenarioCategory = scenarioCategory;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExpectedService() {
        return expectedService;
    }

    public void setExpectedService(String expectedService) {
        this.expectedService = expectedService;
    }

    public String getPredictedService() {
        return predictedService;
    }

    public void setPredictedService(String predictedService) {
        this.predictedService = predictedService;
    }

    public boolean isTop1Match() {
        return top1Match;
    }

    public void setTop1Match(boolean top1Match) {
        this.top1Match = top1Match;
    }

    public boolean isTop3Match() {
        return top3Match;
    }

    public void setTop3Match(boolean top3Match) {
        this.top3Match = top3Match;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public boolean isEvidenceGrounded() {
        return evidenceGrounded;
    }

    public void setEvidenceGrounded(boolean evidenceGrounded) {
        this.evidenceGrounded = evidenceGrounded;
    }

    public boolean isHallucinationDetected() {
        return hallucinationDetected;
    }

    public void setHallucinationDetected(boolean hallucinationDetected) {
        this.hallucinationDetected = hallucinationDetected;
    }

    public boolean isInsufficientEvidenceCorrect() {
        return insufficientEvidenceCorrect;
    }

    public void setInsufficientEvidenceCorrect(boolean insufficientEvidenceCorrect) {
        this.insufficientEvidenceCorrect = insufficientEvidenceCorrect;
    }

    public int getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(int toolCalls) {
        this.toolCalls = toolCalls;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(int tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getMetricsJson() {
        return metricsJson;
    }

    public void setMetricsJson(String metricsJson) {
        this.metricsJson = metricsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
