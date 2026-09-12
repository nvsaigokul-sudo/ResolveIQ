package com.resolveiq.backend.domain;

import com.resolveiq.common.incident.VerificationStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Root-cause hypothesis candidate generated for an incident (PRD §19.3, §24, §57).
 * Engineers explicitly mark AI-generated root-cause candidates as:
 * Verified / Rejected / Needs More Evidence.
 */
@Entity
@Table(name = "root_cause_candidates")
public class RootCauseCandidateEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(nullable = false)
    private int rank;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String hypothesis;

    @Column(name = "root_service", nullable = false)
    private String rootService;

    @Column(nullable = false)
    private Double confidence;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RootCauseCandidateEntity() {
    }

    public RootCauseCandidateEntity(UUID tenantId, UUID incidentId, UUID investigationId,
                                    int rank, String hypothesis, String rootService,
                                    Double confidence, String reasoning) {
        super(tenantId);
        this.incidentId = incidentId;
        this.investigationId = investigationId;
        this.rank = rank;
        this.hypothesis = hypothesis;
        this.rootService = rootService;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.verificationStatus = VerificationStatus.UNVERIFIED;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getInvestigationId() {
        return investigationId;
    }

    public void setInvestigationId(UUID investigationId) {
        this.investigationId = investigationId;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(UUID incidentId) {
        this.incidentId = incidentId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getHypothesis() {
        return hypothesis;
    }

    public void setHypothesis(String hypothesis) {
        this.hypothesis = hypothesis;
    }

    public String getRootService() {
        return rootService;
    }

    public void setRootService(String rootService) {
        this.rootService = rootService;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(UUID verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public String getVerificationNotes() {
        return verificationNotes;
    }

    public void setVerificationNotes(String verificationNotes) {
        this.verificationNotes = verificationNotes;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
