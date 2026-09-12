package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.IncidentEventEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentEventRepository extends TenantScopedRepository<IncidentEventEntity, UUID> {

    List<IncidentEventEntity> findByIncidentIdAndTenantIdOrderByCreatedAtAsc(UUID incidentId, UUID tenantId);
}
