package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.CandidateEvidenceEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CandidateEvidenceRepository extends TenantScopedRepository<CandidateEvidenceEntity, UUID> {

    List<CandidateEvidenceEntity> findByCandidateIdAndTenantId(UUID candidateId, UUID tenantId);

    List<CandidateEvidenceEntity> findByEvidenceIdAndTenantId(UUID evidenceId, UUID tenantId);
}
