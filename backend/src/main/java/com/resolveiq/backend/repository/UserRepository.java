package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends TenantScopedRepository<UserEntity, UUID> {
    Optional<UserEntity> findByTenantIdAndEmail(UUID tenantId, String email);
}
