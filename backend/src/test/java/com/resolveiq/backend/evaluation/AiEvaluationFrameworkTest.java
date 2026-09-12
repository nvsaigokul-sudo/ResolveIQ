package com.resolveiq.backend.evaluation;

import com.resolveiq.backend.api.EvaluationController;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.domain.ServiceEntity;
import com.resolveiq.backend.evaluation.domain.EvaluationRunEntity;
import com.resolveiq.backend.evaluation.dto.EvaluationRegressionDto;
import com.resolveiq.backend.evaluation.dto.EvaluationReportDto;
import com.resolveiq.backend.evaluation.dto.EvaluationRunRequest;
import com.resolveiq.backend.evaluation.repository.EvaluationCaseResultRepository;
import com.resolveiq.backend.evaluation.repository.EvaluationRunRepository;
import com.resolveiq.backend.evaluation.service.AiEvaluationService;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.ProjectRepository;
import com.resolveiq.backend.repository.ServiceRepository;
import com.resolveiq.common.dto.ApiResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test suite for ResolveIQ AI Evaluation Framework (PRD §§30, 56, 58, 60).
 * Verifies benchmark execution, Top-1/Top-3 scoring, evidence grounding, regression comparison,
 * and REST API endpoints.
 */
@SpringBootTest
@ActiveProfiles("test")
public class AiEvaluationFrameworkTest {

    @Autowired private AiEvaluationService aiEvaluationService;
    @Autowired private EvaluationController evaluationController;
    @Autowired private EvaluationRunRepository evaluationRunRepository;
    @Autowired private EvaluationCaseResultRepository evaluationCaseResultRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ServiceRepository serviceRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        OrganizationEntity org = organizationRepository.save(
                new OrganizationEntity("Eval Org", "eval-org-" + uniqueSuffix, "ENTERPRISE"));
        tenantId = org.getId();

        ProjectEntity proj = projectRepository.save(
                new ProjectEntity(tenantId, "Eval Project", "eval-proj-" + uniqueSuffix, "desc"));

        // Register standard services
        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "api-gateway", "TIER_1", "gw", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "payment-service", "TIER_1", "pay", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "order-service", "TIER_1", "ord", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "user-service", "TIER_1", "usr", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "inventory-service", "TIER_1", "inv", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "notification-service", "TIER_2", "notif", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "auth-service", "TIER_1", "auth", "repo"));

        // Set SRE security context
        TenantContextHolder.setContext(new TenantContext(tenantId, UUID.randomUUID(), Role.SRE, ActorType.USER));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("AI Evaluation Service executes benchmark suite, scores metrics, and persists run and case results")
    void testEvaluationSuiteExecutionAndScoring() {
        // Run simulator benchmark suite
        EvaluationRunRequest req = new EvaluationRunRequest("simulator", null, null);
        EvaluationReportDto report = aiEvaluationService.runEvaluation(req);

        assertNotNull(report);
        assertNotNull(report.runId());
        assertEquals(tenantId, report.tenantId());
        assertTrue(report.totalCases() >= 10, "Simulator suite must evaluate at least 10 cases");
        assertTrue(report.passedCases() > 0, "Must have passed cases");

        // Validate PRD metrics
        assertTrue(report.top1Accuracy() >= 0.70, "Top-1 accuracy must be >= 70%");
        assertTrue(report.top3Accuracy() >= report.top1Accuracy(), "Top-3 accuracy must be >= Top-1 accuracy");
        assertTrue(report.evidenceGrounding() >= 0.75, "Evidence grounding must be >= 75%");
        assertTrue(report.hallucinationRate() <= 0.10, "Hallucination rate must be <= 10%");
        assertEquals(1.0, report.insufficientEvidenceAccuracy(), "Insufficient evidence accuracy must be 100%");
        assertTrue(report.avgToolCalls() <= 12.0, "Average tool calls must be within safeguard budget");
        assertTrue(report.p50DurationMs() >= 0);
        assertTrue(report.p95DurationMs() >= report.p50DurationMs());

        // Validate database persistence
        EvaluationRunEntity persistedRun = evaluationRunRepository.findByIdAndTenantId(report.runId(), tenantId)
                .orElseThrow();
        assertEquals(report.totalCases(), persistedRun.getTotalCases());
        assertEquals(report.passedCases(), persistedRun.getPassedCases());

        // Validate case results persistence
        assertFalse(report.caseResults().isEmpty());
        assertEquals(report.totalCases(), report.caseResults().size());
    }

    @Test
    @DisplayName("Regression Detection: Detects drop in accuracy or grounding against baseline evaluation run")
    void testRegressionDetectionAgainstBaseline() {
        // 1. Establish baseline run
        EvaluationRunRequest req1 = new EvaluationRunRequest("simulator", null, null);
        EvaluationReportDto baseline = aiEvaluationService.runEvaluation(req1);

        // 2. Execute second run specifying baseline
        EvaluationRunRequest req2 = new EvaluationRunRequest("simulator", baseline.runId(), null);
        EvaluationReportDto current = aiEvaluationService.runEvaluation(req2);

        // 3. Compare runs
        EvaluationRegressionDto comp = aiEvaluationService.compareRuns(current.runId(), baseline.runId());
        assertNotNull(comp);
        assertEquals(current.runId(), comp.currentRunId());
        assertEquals(baseline.runId(), comp.baselineRunId());
        assertNotNull(comp.regressionReasons());
    }

    @Test
    @DisplayName("Evaluation Controller REST API: Triggers evaluation, lists runs, and returns detailed report")
    void testEvaluationControllerRestApi() {
        // Trigger run via REST API
        EvaluationRunRequest req = new EvaluationRunRequest(
                "simulator",
                null,
                List.of("scenario-01-bad-deployment", "scenario-06-legitimate-traffic-spike")
        );
        ResponseEntity<ApiResponse<EvaluationReportDto>> postResp = evaluationController.runEvaluation(req);
        assertEquals(HttpStatus.CREATED, postResp.getStatusCode());
        assertNotNull(postResp.getBody());
        EvaluationReportDto report = postResp.getBody().data();
        assertNotNull(report.runId());
        assertEquals(2, report.totalCases());

        // List runs
        ResponseEntity<ApiResponse<List<EvaluationRunEntity>>> listResp = evaluationController.listRuns();
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        assertNotNull(listResp.getBody());
        assertFalse(listResp.getBody().data().isEmpty());

        // Get single run report
        ResponseEntity<ApiResponse<EvaluationReportDto>> getResp = evaluationController.getReport(report.runId());
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertNotNull(getResp.getBody());
        assertEquals(report.runId(), getResp.getBody().data().runId());
    }
}
