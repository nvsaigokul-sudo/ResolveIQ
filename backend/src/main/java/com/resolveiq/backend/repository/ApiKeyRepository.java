package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.ApiKeyEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends TenantScopedRepository<ApiKeyEntity, UUID> {
    List<ApiKeyEntity> findAllByKeyPrefixAndRevokedAtIsNull(String keyPrefix);
}
