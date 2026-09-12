package com.resolveiq.backend.evaluation;

import com.resolveiq.backend.api.EvaluationController;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.domain.ServiceEntity;
import com.resolveiq.backend.evaluation.domain.EvaluationRunEntity;
import com.resolveiq.backend.evaluation.dto.EvaluationReportDto;
import com.resolveiq.backend.evaluation.dto.EvaluationRunRequest;
import com.resolveiq.backend.evaluation.service.AiEvaluationService;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.ProjectRepository;
import com.resolveiq.backend.repository.ServiceRepository;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.security.ActorType;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for Multi-Tenant Isolation across the AI Evaluation Framework (PRD §§11.1, 35.3, 56).
 */
@SpringBootTest
@ActiveProfiles("test")
public class MultiTenantEvaluationIsolationTest {

    @Autowired private AiEvaluationService aiEvaluationService;
    @Autowired private EvaluationController evaluationController;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ServiceRepository serviceRepository;

    private UUID tenantAlphaId;
    private UUID tenantBetaId;

    @BeforeEach
    void setUp() {
        String suffixA = UUID.randomUUID().toString().substring(0, 8);
        String suffixB = UUID.randomUUID().toString().substring(0, 8);

        OrganizationEntity orgA = organizationRepository.save(new OrganizationEntity("Alpha Org", "alpha-" + suffixA, "ENTERPRISE"));
        tenantAlphaId = orgA.getId();

        OrganizationEntity orgB = organizationRepository.save(new OrganizationEntity("Beta Org", "beta-" + suffixB, "ENTERPRISE"));
        tenantBetaId = orgB.getId();

        ProjectEntity projA = projectRepository.save(new ProjectEntity(tenantAlphaId, "Alpha Proj", "proj-a-" + suffixA, "desc"));
        serviceRepository.save(new ServiceEntity(tenantAlphaId, projA.getId(), "payment-service", "TIER_1", "pay", "repo"));

        ProjectEntity projB = projectRepository.save(new ProjectEntity(tenantBetaId, "Beta Proj", "proj-b-" + suffixB, "desc"));
        serviceRepository.save(new ServiceEntity(tenantBetaId, projB.getId(), "payment-service", "TIER_1", "pay", "repo"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Multi-Tenant Isolation: Tenant Beta cannot access Tenant Alpha's evaluation report")
    void testCrossTenantEvaluationRunAccessBlocked() {
        // 1. Run evaluation under Tenant Alpha
        TenantContextHolder.setContext(new TenantContext(tenantAlphaId, UUID.randomUUID(), Role.SRE, ActorType.USER));
        EvaluationRunRequest req = new EvaluationRunRequest("simulator", null, List.of("scenario-01-bad-deployment"));
        EvaluationReportDto reportAlpha = aiEvaluationService.runEvaluation(req);
        UUID alphaRunId = reportAlpha.runId();

        // 2. Switch to Tenant Beta
        TenantContextHolder.setContext(new TenantContext(tenantBetaId, UUID.randomUUID(), Role.SRE, ActorType.USER));

        // 3. Attempt to fetch Tenant Alpha's report
        assertThrows(ResourceNotFoundException.class, () -> evaluationController.getReport(alphaRunId),
                "Cross-tenant access to evaluation run must throw 404 ResourceNotFoundException");

        // 4. Verify listing under Tenant Beta returns 0 runs
        ResponseEntity<ApiResponse<List<EvaluationRunEntity>>> listBeta = evaluationController.listRuns();
        assertNotNull(listBeta.getBody());
        assertTrue(listBeta.getBody().data().isEmpty(), "Tenant Beta must see 0 evaluation runs");
    }
}
