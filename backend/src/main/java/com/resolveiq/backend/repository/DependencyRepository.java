package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.DependencyEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DependencyRepository extends TenantScopedRepository<DependencyEntity, UUID> {
    List<DependencyEntity> findAllByTenantIdAndUpstreamServiceId(UUID tenantId, UUID upstreamServiceId);
    List<DependencyEntity> findAllByTenantIdAndDownstreamServiceId(UUID tenantId, UUID downstreamServiceId);
}
