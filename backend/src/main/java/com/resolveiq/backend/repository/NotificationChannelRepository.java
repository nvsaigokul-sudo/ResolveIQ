package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.NotificationChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationChannelRepository extends JpaRepository<NotificationChannelEntity, UUID> {

    List<NotificationChannelEntity> findByTenantId(UUID tenantId);

    List<NotificationChannelEntity> findByTenantIdAndEnabledTrue(UUID tenantId);

    Optional<NotificationChannelEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndName(UUID tenantId, String name);
}
