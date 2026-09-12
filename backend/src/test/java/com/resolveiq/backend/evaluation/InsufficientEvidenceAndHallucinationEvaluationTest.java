package com.resolveiq.backend.evaluation;

import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.evaluation.dto.EvaluationCaseResultDto;
import com.resolveiq.backend.evaluation.dto.EvaluationReportDto;
import com.resolveiq.backend.evaluation.dto.EvaluationRunRequest;
import com.resolveiq.backend.evaluation.service.AiEvaluationService;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.ProjectRepository;
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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verification test suite for Insufficient Evidence Correctness, Unsupported Claim Detection,
 * and Hallucination Prevention (PRD §§23, 24, 56, 58).
 */
@SpringBootTest
@ActiveProfiles("test")
public class InsufficientEvidenceAndHallucinationEvaluationTest {

    @Autowired private AiEvaluationService aiEvaluationService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        OrganizationEntity org = organizationRepository.save(
                new OrganizationEntity("Abstention Org", "abstention-org-" + uniqueSuffix, "ENTERPRISE"));
        tenantId = org.getId();

        projectRepository.save(new ProjectEntity(tenantId, "Abstention Project", "abstention-proj-" + uniqueSuffix, "desc"));

        TenantContextHolder.setContext(new TenantContext(tenantId, UUID.randomUUID(), Role.SRE, ActorType.USER));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Insufficient Evidence: Agent correctly outputs insufficientEvidence: true on missing telemetry")
    void testInsufficientEvidenceCorrectness() {
        EvaluationRunRequest req = new EvaluationRunRequest(
                "insufficient_evidence",
                null,
                List.of("scenario-11-insufficient-evidence")
        );
        EvaluationReportDto report = aiEvaluationService.runEvaluation(req);

        assertNotNull(report);
        assertEquals(1, report.totalCases());
        assertEquals(1, report.passedCases());
        assertEquals(1.0, report.insufficientEvidenceAccuracy(), "Must achieve 100% accuracy on insufficient evidence abstention");

        EvaluationCaseResultDto caseResult = report.caseResults().get(0);
        assertTrue(caseResult.insufficientEvidenceCorrect());
        assertFalse(caseResult.hallucinationDetected());
        assertEquals("PASSED", caseResult.status());
    }

    @Test
    @DisplayName("Historical Similarity Trap: Agent avoids hallucinating past cause when current evidence is absent")
    void testHistoricalSimilarityWithoutEvidence() {
        EvaluationRunRequest req = new EvaluationRunRequest(
                "insufficient_evidence",
                null,
                List.of("scenario-15-historical-similarity-no-evidence")
        );
        EvaluationReportDto report = aiEvaluationService.runEvaluation(req);

        assertNotNull(report);
        assertEquals(1, report.totalCases());

        EvaluationCaseResultDto caseResult = report.caseResults().get(0);
        assertTrue(caseResult.insufficientEvidenceCorrect(), "Agent must not jump to historical conclusion without evidence");
        assertFalse(caseResult.hallucinationDetected(), "Zero hallucinations permitted on historical similarity trap");
    }
}
