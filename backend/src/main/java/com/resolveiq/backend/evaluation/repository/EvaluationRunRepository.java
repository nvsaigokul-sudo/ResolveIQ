package com.resolveiq.backend.evaluation.repository;

import com.resolveiq.backend.evaluation.domain.EvaluationRunEntity;
import com.resolveiq.backend.repository.TenantScopedRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationRunRepository extends TenantScopedRepository<EvaluationRunEntity, UUID> {

    List<EvaluationRunEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<EvaluationRunEntity> findByTenantIdAndBenchmarkSuiteOrderByCreatedAtDesc(UUID tenantId, String benchmarkSuite);

    Optional<EvaluationRunEntity> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
