package com.resolveiq.backend.evaluation.domain;

import com.resolveiq.backend.domain.TenantScopedEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted record of an AI Evaluation benchmark execution run (PRD §30, §56, §58).
 */
@Entity
@Table(name = "evaluation_runs")
public class EvaluationRunEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_identifier", nullable = false)
    private String modelIdentifier;

    @Column(name = "benchmark_suite", nullable = false)
    private String benchmarkSuite;

    @Column(name = "total_cases", nullable = false)
    private int totalCases;

    @Column(name = "passed_cases", nullable = false)
    private int passedCases;

    @Column(name = "top1_accuracy", nullable = false)
    private double top1Accuracy;

    @Column(name = "top3_accuracy", nullable = false)
    private double top3Accuracy;

    @Column(name = "evidence_precision", nullable = false)
    private double evidencePrecision;

    @Column(name = "evidence_grounding", nullable = false)
    private double evidenceGrounding;

    @Column(name = "hallucination_rate", nullable = false)
    private double hallucinationRate;

    @Column(name = "insufficient_evidence_accuracy", nullable = false)
    private double insufficientEvidenceAccuracy;

    @Column(name = "completion_rate", nullable = false)
    private double completionRate;

    @Column(name = "avg_tool_calls", nullable = false)
    private double avgToolCalls;

    @Column(name = "p50_duration_ms", nullable = false)
    private long p50DurationMs;

    @Column(name = "p95_duration_ms", nullable = false)
    private long p95DurationMs;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "estimated_cost_usd", nullable = false)
    private double estimatedCostUsd;

    @Column(name = "regression_detected", nullable = false)
    private boolean regressionDetected;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public EvaluationRunEntity() {}

    public EvaluationRunEntity(
            UUID tenantId,
            String modelIdentifier,
            String benchmarkSuite,
            int totalCases,
            int passedCases,
            double top1Accuracy,
            double top3Accuracy,
            double evidencePrecision,
            double evidenceGrounding,
            double hallucinationRate,
            double insufficientEvidenceAccuracy,
            double completionRate,
            double avgToolCalls,
            long p50DurationMs,
            long p95DurationMs,
            int totalTokens,
            double estimatedCostUsd,
            boolean regressionDetected) {
        super(tenantId);
        this.modelIdentifier = modelIdentifier;
        this.benchmarkSuite = benchmarkSuite;
        this.totalCases = totalCases;
        this.passedCases = passedCases;
        this.top1Accuracy = top1Accuracy;
        this.top3Accuracy = top3Accuracy;
        this.evidencePrecision = evidencePrecision;
        this.evidenceGrounding = evidenceGrounding;
        this.hallucinationRate = hallucinationRate;
        this.insufficientEvidenceAccuracy = insufficientEvidenceAccuracy;
        this.completionRate = completionRate;
        this.avgToolCalls = avgToolCalls;
        this.p50DurationMs = p50DurationMs;
        this.p95DurationMs = p95DurationMs;
        this.totalTokens = totalTokens;
        this.estimatedCostUsd = estimatedCostUsd;
        this.regressionDetected = regressionDetected;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getModelIdentifier() {
        return modelIdentifier;
    }

    public void setModelIdentifier(String modelIdentifier) {
        this.modelIdentifier = modelIdentifier;
    }

    public String getBenchmarkSuite() {
        return benchmarkSuite;
    }

    public void setBenchmarkSuite(String benchmarkSuite) {
        this.benchmarkSuite = benchmarkSuite;
    }

    public int getTotalCases() {
        return totalCases;
    }

    public void setTotalCases(int totalCases) {
        this.totalCases = totalCases;
    }

    public int getPassedCases() {
        return passedCases;
    }

    public void setPassedCases(int passedCases) {
        this.passedCases = passedCases;
    }

    public double getTop1Accuracy() {
        return top1Accuracy;
    }

    public void setTop1Accuracy(double top1Accuracy) {
        this.top1Accuracy = top1Accuracy;
    }

    public double getTop3Accuracy() {
        return top3Accuracy;
    }

    public void setTop3Accuracy(double top3Accuracy) {
        this.top3Accuracy = top3Accuracy;
    }

    public double getEvidencePrecision() {
        return evidencePrecision;
    }

    public void setEvidencePrecision(double evidencePrecision) {
        this.evidencePrecision = evidencePrecision;
    }

    public double getEvidenceGrounding() {
        return evidenceGrounding;
    }

    public void setEvidenceGrounding(double evidenceGrounding) {
        this.evidenceGrounding = evidenceGrounding;
    }

    public double getHallucinationRate() {
        return hallucinationRate;
    }

    public void setHallucinationRate(double hallucinationRate) {
        this.hallucinationRate = hallucinationRate;
    }

    public double getInsufficientEvidenceAccuracy() {
        return insufficientEvidenceAccuracy;
    }

    public void setInsufficientEvidenceAccuracy(double insufficientEvidenceAccuracy) {
        this.insufficientEvidenceAccuracy = insufficientEvidenceAccuracy;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(double completionRate) {
        this.completionRate = completionRate;
    }

    public double getAvgToolCalls() {
        return avgToolCalls;
    }

    public void setAvgToolCalls(double avgToolCalls) {
        this.avgToolCalls = avgToolCalls;
    }

    public long getP50DurationMs() {
        return p50DurationMs;
    }

    public void setP50DurationMs(long p50DurationMs) {
        this.p50DurationMs = p50DurationMs;
    }

    public long getP95DurationMs() {
        return p95DurationMs;
    }

    public void setP95DurationMs(long p95DurationMs) {
        this.p95DurationMs = p95DurationMs;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public double getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public void setEstimatedCostUsd(double estimatedCostUsd) {
        this.estimatedCostUsd = estimatedCostUsd;
    }

    public boolean isRegressionDetected() {
        return regressionDetected;
    }

    public void setRegressionDetected(boolean regressionDetected) {
        this.regressionDetected = regressionDetected;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
