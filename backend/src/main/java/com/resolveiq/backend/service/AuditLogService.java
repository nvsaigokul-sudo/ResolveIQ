package com.resolveiq.backend.service;

import com.resolveiq.backend.domain.AuditLogEntity;
import com.resolveiq.backend.repository.AuditLogRepository;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing append-only immutable audit logs (PRD Section 39).
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogEntity record(UUID tenantId, UUID actorId, String actorType, String action,
                                  String targetResource, String beforeState, String afterState,
                                  String ipAddress, String traceId) {
        AuditLogEntity entry = new AuditLogEntity(
                tenantId,
                actorId,
                actorType != null ? actorType : "SYSTEM",
                action,
                targetResource,
                beforeState,
                afterState,
                ipAddress,
                traceId
        );
        return auditLogRepository.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLogEntity recordCurrentContext(String action, String targetResource, String beforeState, String afterState) {
        TenantContext ctx = TenantContextHolder.getContext().orElse(null);
        if (ctx == null) {
            return null;
        }
        return record(
                ctx.tenantId(),
                ctx.userId() != null ? ctx.userId() : ctx.apiKeyId(),
                ctx.actorType().name(),
                action,
                targetResource,
                beforeState,
                afterState,
                null,
                ctx.traceId()
        );
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntity> getAuditLogsForCurrentTenant() {
        return auditLogRepository.findAllByTenantIdOrderByCreatedAtDesc(TenantContextHolder.getRequiredTenantId());
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntity> getAuditLogsByAction(String action) {
        return auditLogRepository.findAllByTenantIdAndActionOrderByCreatedAtDesc(
                TenantContextHolder.getRequiredTenantId(),
                action
        );
    }

    /**
     * Rejection check: Updates or deletes to audit logs are strictly rejected.
     */
    public void rejectModification() {
        throw new UnsupportedOperationException("Audit logs are strictly immutable: updates and deletes are prohibited");
    }
}
