package com.resolveiq.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all tenant-scoped database entities.
 * Enforces non-null tenant_id at the JPA lifecycle layer.
 */
@MappedSuperclass
public abstract class TenantScopedEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    protected TenantScopedEntity() {
    }

    protected TenantScopedEntity(UUID tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null on TenantScopedEntity");
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    @PrePersist
    protected void validateTenantId() {
        if (this.tenantId == null) {
            throw new IllegalStateException("Attempted to persist tenant-scoped entity without a tenant_id");
        }
    }
}
