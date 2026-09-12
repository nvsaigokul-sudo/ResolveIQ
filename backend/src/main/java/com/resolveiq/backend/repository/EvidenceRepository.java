package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.EvidenceEntity;
import com.resolveiq.common.incident.EvidenceSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvidenceRepository extends TenantScopedRepository<EvidenceEntity, UUID> {

    List<EvidenceEntity> findByIncidentIdAndTenantIdOrderByCreatedAtDesc(UUID incidentId, UUID tenantId);

    List<EvidenceEntity> findByIncidentIdAndTenantId(UUID incidentId, UUID tenantId);

    List<EvidenceEntity> findByIncidentIdAndTenantIdAndSource(UUID incidentId, UUID tenantId, EvidenceSource source);

    List<EvidenceEntity> findByIncidentIdAndTenantIdAndService(UUID incidentId, UUID tenantId, String service);
}
