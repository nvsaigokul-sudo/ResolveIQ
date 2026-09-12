package com.resolveiq.backend.domain;

import com.resolveiq.common.incident.EvidenceRole;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Join entity linking root-cause candidates to specific supporting or counter evidence items (PRD §30, §36.2).
 */
@Entity
@Table(name = "candidate_evidence")
public class CandidateEvidenceEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "evidence_id", nullable = false)
    private UUID evidenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceRole role = EvidenceRole.SUPPORTING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public CandidateEvidenceEntity() {
    }

    public CandidateEvidenceEntity(UUID tenantId, UUID candidateId, UUID evidenceId, EvidenceRole role) {
        super(tenantId);
        this.candidateId = candidateId;
        this.evidenceId = evidenceId;
        this.role = role != null ? role : EvidenceRole.SUPPORTING;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public UUID getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(UUID evidenceId) {
        this.evidenceId = evidenceId;
    }

    public EvidenceRole getRole() {
        return role;
    }

    public void setRole(EvidenceRole role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
