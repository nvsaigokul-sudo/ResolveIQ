package com.resolveiq.backend.investigation;

import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.investigation.dto.InvestigationResultDto;
import com.resolveiq.backend.investigation.dto.RootCauseCandidateDto;
import com.resolveiq.backend.investigation.dto.StructuredRcaDto;
import com.resolveiq.backend.investigation.service.InvestigationService;
import com.resolveiq.backend.repository.*;
import com.resolveiq.common.incident.IncidentEventType;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import com.resolveiq.common.incident.InvestigationStatus;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class EvidenceGroundingAndRcaTest {

    @Autowired private InvestigationService investigationService;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private InvestigationRepository investigationRepository;
    @Autowired private RootCauseCandidateRepository candidateRepository;
    @Autowired private CandidateEvidenceRepository candidateEvidenceRepository;
    @Autowired private EvidenceRepository evidenceRepository;
    @Autowired private IncidentEventRepository incidentEventRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private DeploymentRepository deploymentRepository;

    private UUID tenantId;
    private UUID incidentId;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("Grounding Org", "grounding-org-" + uniqueSuffix, "ENTERPRISE"));
        tenantId = org.getId();

        ProjectEntity proj = projectRepository.save(new ProjectEntity(tenantId, "Grounding Proj", "grounding-proj-" + uniqueSuffix, "desc"));
        ServiceEntity paymentService = serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "payment-service", "TIER_1", "payments-team", "repo"));

        IncidentEntity incident = new IncidentEntity(
                tenantId,
                proj.getId(),
                "grounding-fp-" + UUID.randomUUID(),
                "Payment Service Database Connection Pool Saturation",
                IncidentStatus.INVESTIGATING,
                IncidentSeverity.SEV1,
                "payment-service",
                "[\"payment-service\", \"order-service\"]",
                "Downstream timeouts observed during checkout processing."
        );
        incident = incidentRepository.save(incident);
        incidentId = incident.getId();

        // Seed deployment
        deploymentRepository.save(new DeploymentEntity(
                tenantId, proj.getId(), paymentService.getId(), "payment-service",
                "production", "v2.8", "d7a4b81", "feat(pool): restrict connection pool for cost optimization",
                "ci-deployer", "SUCCESS", Instant.now().minusSeconds(700), "{}"
        ));

        // Establish SRE tenant context
        TenantContextHolder.setContext(new TenantContext(tenantId, UUID.randomUUID(), Role.SRE, ActorType.USER));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Investigation generates evidence-grounded candidates and persists 100% of candidate evidence links")
    void testInvestigationEvidenceGrounding() {
        InvestigationResultDto result = investigationService.investigate(incidentId, "Automated alert trigger");

        assertNotNull(result);
        assertEquals(InvestigationStatus.COMPLETED, result.status());
        assertNotNull(result.structuredRca());

        StructuredRcaDto rca = result.structuredRca();
        assertNotNull(rca.summary());
        assertFalse(rca.candidates().isEmpty(), "Must produce at least one candidate");

        // Verify RootCauseCandidate persistence and evidence links
        List<RootCauseCandidateEntity> persistedCandidates = candidateRepository
                .findByIncidentIdAndTenantIdOrderByRankAsc(incidentId, tenantId);
        assertFalse(persistedCandidates.isEmpty());

        for (RootCauseCandidateEntity cand : persistedCandidates) {
            assertTrue(cand.getConfidence() >= 0.0 && cand.getConfidence() <= 1.0,
                    "Confidence must be bounded in [0.0, 1.0]");
            assertNotNull(cand.getHypothesis());
            assertNotNull(cand.getReasoning());

            // Verify join table candidate_evidence has supporting evidence links (PRD §24)
            List<CandidateEvidenceEntity> links = candidateEvidenceRepository.findByCandidateIdAndTenantId(cand.getId(), tenantId);
            assertFalse(links.isEmpty(), "Every RCA candidate MUST be linked to persisted evidence");
        }
    }

    @Test
    @DisplayName("High-confidence RCA candidate advances incident status from INVESTIGATING to IDENTIFIED")
    void testHighConfidenceAdvancesIncidentStatus() {
        investigationService.investigate(incidentId, "High severity checkout breach");

        IncidentEntity updatedIncident = incidentRepository.findByIdAndTenantId(incidentId, tenantId).orElseThrow();
        assertEquals(IncidentStatus.IDENTIFIED, updatedIncident.getStatus(),
                "Incident status should advance to IDENTIFIED when candidate confidence exceeds 0.75");

        // Verify timeline contains investigation completed and status change
        List<IncidentEventEntity> timeline = incidentEventRepository
                .findByIncidentIdAndTenantIdOrderByCreatedAtAsc(incidentId, tenantId);

        boolean hasStatusChanged = timeline.stream().anyMatch(e -> e.getEventType() == IncidentEventType.STATUS_CHANGED);
        boolean hasInvestigationCompleted = timeline.stream().anyMatch(e -> e.getEventType() == IncidentEventType.INVESTIGATION_COMPLETED);

        assertTrue(hasStatusChanged, "Timeline must record status change");
        assertTrue(hasInvestigationCompleted, "Timeline must record investigation completion");
    }

    @Test
    @DisplayName("Structured RCA DTO contains all mandatory fields per PRD §24")
    void testStructuredRcaSchemaCompleteness() {
        InvestigationResultDto result = investigationService.investigate(incidentId, "Schema test trigger");
        StructuredRcaDto rca = result.structuredRca();

        assertNotNull(rca.summary(), "Summary is required");
        assertNotNull(rca.impact(), "Impact is required");
        assertNotNull(rca.impact().affectedServices());
        assertNotNull(rca.timeline(), "Timeline is required");
        assertFalse(rca.timeline().isEmpty(), "Timeline must contain milestones");
        assertNotNull(rca.candidates(), "Candidates are required");
        assertNotNull(rca.contributingFactors(), "Contributing factors are required");
        assertNotNull(rca.recommendedActions(), "Recommended actions are required");
        assertFalse(rca.recommendedActions().isEmpty(), "Must contain remediation actions");
        assertNotNull(rca.uncertaintyStatement(), "Uncertainty statement is required");
    }
}
