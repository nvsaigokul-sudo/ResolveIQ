package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.CodeRepositoryEntity;
import com.resolveiq.common.integration.GitProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CodeRepositoryRepository extends JpaRepository<CodeRepositoryEntity, UUID> {

    List<CodeRepositoryEntity> findByTenantId(UUID tenantId);

    List<CodeRepositoryEntity> findByTenantIdAndEnabledTrue(UUID tenantId);

    Optional<CodeRepositoryEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CodeRepositoryEntity> findByTenantIdAndRepoName(UUID tenantId, String repoName);

    boolean existsByTenantIdAndRepoName(UUID tenantId, String repoName);

    boolean existsByTenantIdAndProviderAndRepoName(UUID tenantId, GitProviderType provider, String repoName);
}
