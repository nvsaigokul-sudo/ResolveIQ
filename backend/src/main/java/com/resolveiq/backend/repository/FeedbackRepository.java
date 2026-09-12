package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.FeedbackEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends TenantScopedRepository<FeedbackEntity, UUID> {

    List<FeedbackEntity> findByIncidentIdAndTenantIdOrderByCreatedAtDesc(UUID incidentId, UUID tenantId);

    List<FeedbackEntity> findByIncidentIdAndTenantId(UUID incidentId, UUID tenantId);

    List<FeedbackEntity> findByCandidateIdAndTenantId(UUID candidateId, UUID tenantId);
}
