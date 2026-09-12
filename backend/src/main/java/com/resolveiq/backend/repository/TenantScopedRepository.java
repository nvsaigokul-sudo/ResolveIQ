package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.TenantScopedEntity;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base repository enforcing PRD Section 11.1 Layer 2:
 * "All JPA/JDBC repositories for tenant-scoped entities extend a base repository that
 * injects tenant_id into every query automatically; there is no repository method
 * that accepts a raw, unscoped query for tenant-scoped tables."
 */
@NoRepositoryBean
public interface TenantScopedRepository<T extends TenantScopedEntity, ID> extends JpaRepository<T, ID> {

    List<T> findAllByTenantId(UUID tenantId);

    Optional<T> findByIdAndTenantId(ID id, UUID tenantId);

    boolean existsByIdAndTenantId(ID id, UUID tenantId);

    void deleteByIdAndTenantId(ID id, UUID tenantId);

    default List<T> findAllForCurrentTenant() {
        return findAllByTenantId(TenantContextHolder.getRequiredTenantId());
    }

    default Optional<T> findByIdForCurrentTenant(ID id) {
        return findByIdAndTenantId(id, TenantContextHolder.getRequiredTenantId());
    }

    default boolean existsByIdForCurrentTenant(ID id) {
        return existsByIdAndTenantId(id, TenantContextHolder.getRequiredTenantId());
    }

    default void deleteByIdForCurrentTenant(ID id) {
        deleteByIdAndTenantId(id, TenantContextHolder.getRequiredTenantId());
    }
}
