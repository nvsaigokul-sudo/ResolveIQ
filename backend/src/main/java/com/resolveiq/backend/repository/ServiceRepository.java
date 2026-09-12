package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.ServiceEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends TenantScopedRepository<ServiceEntity, UUID> {
    List<ServiceEntity> findAllByTenantIdAndProjectId(UUID tenantId, UUID projectId);
    Optional<ServiceEntity> findByTenantIdAndProjectIdAndName(UUID tenantId, UUID projectId, String name);
    Optional<ServiceEntity> findByTenantIdAndName(UUID tenantId, String name);
}
