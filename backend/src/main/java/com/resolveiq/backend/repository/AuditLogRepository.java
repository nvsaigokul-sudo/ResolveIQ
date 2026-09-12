package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.AuditLogEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends TenantScopedRepository<AuditLogEntity, UUID> {
    List<AuditLogEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<AuditLogEntity> findAllByTenantIdAndActionOrderByCreatedAtDesc(UUID tenantId, String action);
}
