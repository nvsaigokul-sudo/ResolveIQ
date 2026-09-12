package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.NotificationEntity;
import com.resolveiq.common.notification.NotificationDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<NotificationEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, NotificationDeliveryStatus status);

    List<NotificationEntity> findByTenantIdAndIncidentIdOrderByCreatedAtDesc(UUID tenantId, UUID incidentId);

    Optional<NotificationEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<NotificationEntity> findByStatusAndNextRetryAtBefore(NotificationDeliveryStatus status, Instant time);

    long countByTenantIdAndCreatedAtAfter(UUID tenantId, Instant after);
}
