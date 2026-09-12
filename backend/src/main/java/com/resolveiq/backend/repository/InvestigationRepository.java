package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.InvestigationEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestigationRepository extends TenantScopedRepository<InvestigationEntity, UUID> {

    Optional<InvestigationEntity> findFirstByIncidentIdAndTenantIdOrderByCreatedAtDesc(UUID incidentId, UUID tenantId);

    List<InvestigationEntity> findByIncidentIdAndTenantIdOrderByCreatedAtDesc(UUID incidentId, UUID tenantId);
}
