package com.resolveiq.backend.evaluation.repository;

import com.resolveiq.backend.evaluation.domain.EvaluationCaseResultEntity;
import com.resolveiq.backend.repository.TenantScopedRepository;

import java.util.List;
import java.util.UUID;

public interface EvaluationCaseResultRepository extends TenantScopedRepository<EvaluationCaseResultEntity, UUID> {

    List<EvaluationCaseResultEntity> findByTenantIdAndRunIdOrderByCreatedAtAsc(UUID tenantId, UUID runId);

    List<EvaluationCaseResultEntity> findByTenantIdAndScenarioIdOrderByCreatedAtDesc(UUID tenantId, String scenarioId);
}
