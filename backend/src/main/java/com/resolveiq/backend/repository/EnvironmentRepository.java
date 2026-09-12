package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.EnvironmentEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvironmentRepository extends TenantScopedRepository<EnvironmentEntity, UUID> {
    List<EnvironmentEntity> findAllByTenantIdAndProjectId(UUID tenantId, UUID projectId);
    Optional<EnvironmentEntity> findByTenantIdAndProjectIdAndName(UUID tenantId, UUID projectId, String name);
}
