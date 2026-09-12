package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends TenantScopedRepository<IncidentEntity, UUID> {

    Optional<IncidentEntity> findByFingerprintAndTenantIdAndStatusNotIn(
            String fingerprint,
            UUID tenantId,
            Collection<IncidentStatus> terminalStatuses
    );

    List<IncidentEntity> findByTenantIdAndStatus(UUID tenantId, IncidentStatus status);

    List<IncidentEntity> findByTenantIdAndSeverity(UUID tenantId, IncidentSeverity severity);

    List<IncidentEntity> findByTenantIdAndRootService(UUID tenantId, String rootService);

    List<IncidentEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
