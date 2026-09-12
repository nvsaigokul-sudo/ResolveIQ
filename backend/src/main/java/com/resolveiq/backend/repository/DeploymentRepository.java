package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.DeploymentEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeploymentRepository extends TenantScopedRepository<DeploymentEntity, UUID> {

    List<DeploymentEntity> findByTenantIdAndServiceNameAndDeployedAtAfterOrderByDeployedAtDesc(
            UUID tenantId, String serviceName, Instant after);

    List<DeploymentEntity> findByTenantIdAndServiceNameOrderByDeployedAtDesc(
            UUID tenantId, String serviceName);

    List<DeploymentEntity> findByTenantIdAndDeployedAtAfterOrderByDeployedAtDesc(
            UUID tenantId, Instant after);
}
