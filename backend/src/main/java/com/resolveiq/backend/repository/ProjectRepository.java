package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.ProjectEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends TenantScopedRepository<ProjectEntity, UUID> {
    Optional<ProjectEntity> findByTenantIdAndSlug(UUID tenantId, String slug);
}
