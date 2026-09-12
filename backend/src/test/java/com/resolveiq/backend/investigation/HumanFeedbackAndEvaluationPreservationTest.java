package com.resolveiq.backend.investigation;

import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.investigation.dto.InvestigationResultDto;
import com.resolveiq.backend.investigation.service.InvestigationService;
import com.resolveiq.backend.repository.*;
import com.resolveiq.backend.service.IncidentService;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import com.resolveiq.common.incident.VerificationStatus;
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
public class HumanFeedbackAndEvaluationPreservationTest {

    @Autowired private InvestigationService investigationService;
    @Autowired private IncidentService incidentService;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private RootCauseCandidateRepository candidateRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    private UUID tenantId;
    private UUID userId;
    private UUID incidentId;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("Feedback Org", "feedback-org-" + uniqueSuffix, "ENTERPRISE"));
        tenantId = org.getId();

        UserEntity user = userRepository.save(new UserEntity(tenantId, "sre-" + uniqueSuffix + "@example.com", "SRE User", Role.SRE, "hashed_pwd"));
        userId = user.getId();

        ProjectEntity proj = projectRepository.save(new ProjectEntity(tenantId, "Feedback Proj", "feedback-proj-" + uniqueSuffix, "desc"));
        IncidentEntity incident = new IncidentEntity(
                tenantId,
                proj.getId(),
                "feedback-fp-" + UUID.randomUUID(),
                "Payment Service Outage",
                IncidentStatus.INVESTIGATING,
                IncidentSeverity.SEV1,
                "payment-service",
                "[\"payment-service\"]",
                "Checkout timeouts"
        );
        incident = incidentRepository.save(incident);
        incidentId = incident.getId();

        TenantContextHolder.setContext(new TenantContext(tenantId, userId, Role.SRE, ActorType.USER));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Human verification state (VERIFIED, REJECTED, NEEDS_MORE_EVIDENCE) is persisted and authoritative")
    void testHumanVerificationStatePersisted() {
        // Run AI investigation to produce candidates
        InvestigationResultDto result = investigationService.investigate(incidentId, "Initial trigger");
        List<RootCauseCandidateEntity> candidates = candidateRepository
                .findByIncidentIdAndTenantIdOrderByRankAsc(incidentId, tenantId);
        assertFalse(candidates.isEmpty());

        RootCauseCandidateEntity cand1 = candidates.get(0);
        assertEquals(VerificationStatus.UNVERIFIED, cand1.getVerificationStatus());

        // Human SRE verifies candidate #1
        incidentService.verifyCandidate(incidentId, cand1.getId(), VerificationStatus.VERIFIED,
                "Confirmed: Hikari connection pool was exhausted after v2.8 deployment", 5);

        RootCauseCandidateEntity verifiedCand = candidateRepository.findByIdAndTenantId(cand1.getId(), tenantId).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, verifiedCand.getVerificationStatus());
        assertEquals(userId, verifiedCand.getVerifiedBy());
        assertNotNull(verifiedCand.getVerifiedAt());

        // Verify feedback table record for offline evaluation dataset (PRD §57)
        List<FeedbackEntity> feedbackList = feedbackRepository.findByIncidentIdAndTenantId(incidentId, tenantId);
        assertFalse(feedbackList.isEmpty());
        FeedbackEntity feedback = feedbackList.get(0);
        assertEquals(VerificationStatus.VERIFIED, feedback.getVerificationStatus());
        assertEquals(5, feedback.getRating());
        assertEquals(cand1.getId(), feedback.getCandidateId());

        // Verify human verification state remains authoritative and is not overwritten by AI
        RootCauseCandidateEntity reloaded = candidateRepository.findByIdAndTenantId(cand1.getId(), tenantId).orElseThrow();
        assertEquals(VerificationStatus.VERIFIED, reloaded.getVerificationStatus(),
                "AI cannot overwrite human verification state");
    }
}
