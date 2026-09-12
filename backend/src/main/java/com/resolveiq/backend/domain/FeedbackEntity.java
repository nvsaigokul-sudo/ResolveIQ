package com.resolveiq.backend.domain;

import com.resolveiq.common.incident.VerificationStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Human feedback entity for RCA verification and offline dataset collection (PRD §36.1, §57).
 */
@Entity
@Table(name = "feedback")
public class FeedbackEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "candidate_id")
    private UUID candidateId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private Integer rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public FeedbackEntity() {
    }

    public FeedbackEntity(UUID tenantId, UUID incidentId, UUID candidateId, UUID userId,
                          VerificationStatus verificationStatus, String comment, Integer rating) {
        super(tenantId);
        this.incidentId = incidentId;
        this.candidateId = candidateId;
        this.userId = userId;
        this.verificationStatus = verificationStatus;
        this.comment = comment;
        this.rating = rating;
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

    public UUID getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
