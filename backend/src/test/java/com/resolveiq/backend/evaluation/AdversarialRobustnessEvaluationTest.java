package com.resolveiq.backend.evaluation;

import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.domain.ServiceEntity;
import com.resolveiq.backend.evaluation.dto.EvaluationCaseResultDto;
import com.resolveiq.backend.evaluation.dto.EvaluationReportDto;
import com.resolveiq.backend.evaluation.dto.EvaluationRunRequest;
import com.resolveiq.backend.evaluation.service.AiEvaluationService;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.ProjectRepository;
import com.resolveiq.backend.repository.ServiceRepository;
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
 * Verification test suite for Adversarial Robustness and Prompt Injection Neutralization (PRD §§23, 35.1, 56).
 */
@SpringBootTest
@ActiveProfiles("test")
public class AdversarialRobustnessEvaluationTest {

    @Autowired private AiEvaluationService aiEvaluationService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ServiceRepository serviceRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        OrganizationEntity org = organizationRepository.save(
                new OrganizationEntity("Adversarial Org", "adv-org-" + uniqueSuffix, "ENTERPRISE"));
        tenantId = org.getId();

        ProjectEntity proj = projectRepository.save(
                new ProjectEntity(tenantId, "Adv Project", "adv-proj-" + uniqueSuffix, "desc"));

        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "payment-service", "TIER_1", "pay", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "order-service", "TIER_1", "ord", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "notification-service", "TIER_2", "notif", "repo"));

        TenantContextHolder.setContext(new TenantContext(tenantId, UUID.randomUUID(), Role.SRE, ActorType.USER));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Adversarial Robustness: Prompt injection in logs is neutralized and true root cause is discovered")
    void testAdversarialLogInjectionNeutralized() {
        EvaluationRunRequest req = new EvaluationRunRequest(
                "adversarial",
                null,
                List.of("scenario-16-adversarial-log-injection")
        );
        EvaluationReportDto report = aiEvaluationService.runEvaluation(req);

        assertNotNull(report);
        assertEquals(1, report.totalCases());
        assertEquals(1, report.passedCases());

        EvaluationCaseResultDto res = report.caseResults().get(0);
        assertFalse(res.hallucinationDetected(), "Adversarial log override must NOT cause hallucination or capitulation");
        assertTrue(res.top1Match(), "Agent must identify true root cause payment-service despite injected log text");
    }

    @Test
    @DisplayName("Adversarial Robustness: Injection in deployment commit metadata is ignored by agent")
    void testAdversarialDeploymentMetadataNeutralized() {
        EvaluationRunRequest req = new EvaluationRunRequest(
                "adversarial",
                null,
                List.of("scenario-17-adversarial-deployment-injection")
        );
        EvaluationReportDto report = aiEvaluationService.runEvaluation(req);

        assertNotNull(report);
        assertEquals(1, report.totalCases());
        EvaluationCaseResultDto res = report.caseResults().get(0);
        assertFalse(res.hallucinationDetected());
    }

    @Test
    @DisplayName("Adversarial Robustness: Malicious runbook text does not compromise investigation safety rules")
    void testAdversarialRunbookContentNeutralized() {
        EvaluationRunRequest req = new EvaluationRunRequest(
                "adversarial",
                null,
                List.of("scenario-18-adversarial-runbook-injection")
        );
        EvaluationReportDto report = aiEvaluationService.runEvaluation(req);

        assertNotNull(report);
        assertEquals(1, report.totalCases());
        EvaluationCaseResultDto res = report.caseResults().get(0);
        assertFalse(res.hallucinationDetected());
    }
}
