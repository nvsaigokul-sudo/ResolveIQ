package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.KnowledgeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, UUID> {

    Optional<KnowledgeChunkEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<KnowledgeChunkEntity> findByTenantIdAndDocIdOrderByChunkIndex(UUID tenantId, UUID docId);

    List<KnowledgeChunkEntity> findByTenantId(UUID tenantId);

    List<KnowledgeChunkEntity> findByTenantIdAndDocIdIn(UUID tenantId, Collection<UUID> docIds);

    long countByTenantId(UUID tenantId);

    @Modifying
    @Query("DELETE FROM KnowledgeChunkEntity c WHERE c.tenantId = :tenantId AND c.docId = :docId")
    void deleteByTenantIdAndDocId(@Param("tenantId") UUID tenantId, @Param("docId") UUID docId);
}
