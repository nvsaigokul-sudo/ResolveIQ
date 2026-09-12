package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.KnowledgeDocEntity;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeDocRepository extends JpaRepository<KnowledgeDocEntity, UUID> {

    Optional<KnowledgeDocEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<KnowledgeDocEntity> findByTenantIdAndContentHash(UUID tenantId, String contentHash);

    Page<KnowledgeDocEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<KnowledgeDocEntity> findByTenantIdAndDocType(UUID tenantId, KnowledgeDocType docType, Pageable pageable);

    Page<KnowledgeDocEntity> findByTenantIdAndService(UUID tenantId, String service, Pageable pageable);

    Page<KnowledgeDocEntity> findByTenantIdAndDocTypeAndService(UUID tenantId, KnowledgeDocType docType, String service, Pageable pageable);

    List<KnowledgeDocEntity> findByTenantId(UUID tenantId);
}
