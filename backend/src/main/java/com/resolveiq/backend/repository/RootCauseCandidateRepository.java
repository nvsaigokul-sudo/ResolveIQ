package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.RootCauseCandidateEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RootCauseCandidateRepository extends TenantScopedRepository<RootCauseCandidateEntity, UUID> {

    List<RootCauseCandidateEntity> findByIncidentIdAndTenantIdOrderByRankAsc(UUID incidentId, UUID tenantId);

    List<RootCauseCandidateEntity> findByInvestigationIdAndTenantIdOrderByRankAsc(UUID investigationId, UUID tenantId);
}
