package com.resolveiq.backend.service;

import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.exception.InvalidLifecycleTransitionException;
import com.resolveiq.backend.kafka.IncidentKafkaProducer;
import com.resolveiq.backend.repository.*;
import com.resolveiq.common.correlation.BlastRadiusResult;
import com.resolveiq.common.correlation.CandidateOriginScore;
import com.resolveiq.common.correlation.ConfidenceTier;
import com.resolveiq.common.correlation.CorrelatedIncidentPayload;
import com.resolveiq.common.correlation.CorrelationSignalBreakdown;
import com.resolveiq.common.incident.*;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IncidentManagementIntegrationTest {

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentEventRepository incidentEventRepository;

    @Autowired
    private RootCauseCandidateRepository rootCauseCandidateRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private InvestigationRepository investigationRepository;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private IncidentKafkaProducer incidentKafkaProducer;

    private OrganizationEntity tenant;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        String slug = "mgmt-org-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("Incident Management Org", slug, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-mgmt-setup"));
        try {
            project = tenantService.createProject("Core Platform", "core-" + slug, "Platform project");
            tenantService.createService(project.getId(), "checkout-service", "TIER_1", "Checkout SRE", "https://git/checkout");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Create canonical incident from CorrelatedIncidentPayload with timeline and evidence (PRD §19, §20)")
    void testCreateIncidentFromCorrelatedIncident() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-create-incident"));
        try {
            CorrelatedIncidentPayload payload = createSamplePayload("fingerprint-test-create", Severity.HIGH, "checkout-service");

            IncidentEntity incident = incidentService.createOrUpdateFromCorrelatedIncident(payload);
            assertThat(incident.getId()).isNotNull();
            assertThat(incident.getTenantId()).isEqualTo(tenant.getId());
            assertThat(incident.getRootService()).isEqualTo("checkout-service");
            assertThat(incident.getSeverity()).isEqualTo(IncidentSeverity.SEV2);
            assertThat(incident.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);

            // Verify timeline events
            List<IncidentEventEntity> timeline = incidentService.getTimeline(incident.getId());
            assertThat(timeline).isNotEmpty();
            assertThat(timeline).extracting(IncidentEventEntity::getEventType)
                    .contains(
                            IncidentEventType.ANOMALY_DETECTED,
                            IncidentEventType.CORRELATION_DISCOVERED,
                            IncidentEventType.INVESTIGATION_STARTED,
                            IncidentEventType.EVIDENCE_DISCOVERED
                    );
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Deduplication & Replay Idempotency: Duplicate fingerprint updates existing open incident (PRD §19.3)")
    void testDeduplicationAndReplayIdempotency() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-dedup"));
        try {
            String fingerprint = "fingerprint-dedup-" + UUID.randomUUID();
            CorrelatedIncidentPayload payload1 = createSamplePayload(fingerprint, Severity.MEDIUM, "checkout-service");
            IncidentEntity incident1 = incidentService.createOrUpdateFromCorrelatedIncident(payload1);

            // Replay identical or newly correlated anomaly under same fingerprint
            CorrelatedIncidentPayload payload2 = createSamplePayload(fingerprint, Severity.MEDIUM, "checkout-service");
            IncidentEntity incident2 = incidentService.createOrUpdateFromCorrelatedIncident(payload2);

            // Must NOT create a second incident row
            assertThat(incident2.getId()).isEqualTo(incident1.getId());

            List<IncidentEntity> allIncidents = incidentRepository.findAllByTenantId(tenant.getId());
            long countForFingerprint = allIncidents.stream()
                    .filter(i -> i.getFingerprint().equals(fingerprint))
                    .count();
            assertThat(countForFingerprint).isEqualTo(1);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Severity Escalation: Higher severity anomaly escalates incident and logs SEVERITY_CHANGED timeline event (PRD §19.3)")
    void testSeverityEscalation() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-escalate"));
        try {
            String fingerprint = "fingerprint-esc-" + UUID.randomUUID();
            CorrelatedIncidentPayload payload1 = createSamplePayload(fingerprint, Severity.MEDIUM, "checkout-service");
            IncidentEntity incident = incidentService.createOrUpdateFromCorrelatedIncident(payload1);
            assertThat(incident.getSeverity()).isEqualTo(IncidentSeverity.SEV3);

            // Escalating event arrives with CRITICAL severity
            CorrelatedIncidentPayload payload2 = createSamplePayload(fingerprint, Severity.CRITICAL, "checkout-service");
            IncidentEntity escalated = incidentService.createOrUpdateFromCorrelatedIncident(payload2);

            assertThat(escalated.getId()).isEqualTo(incident.getId());
            assertThat(escalated.getSeverity()).isEqualTo(IncidentSeverity.SEV1);

            List<IncidentEventEntity> timeline = incidentService.getTimeline(incident.getId());
            assertThat(timeline).anyMatch(e -> e.getEventType() == IncidentEventType.SEVERITY_CHANGED && e.getSummary().contains("escalated"));
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Guarded lifecycle state transitions: Valid transitions update status and timestamps (PRD §19.1)")
    void testGuardedLifecycleTransitions() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-lifecycle"));
        try {
            CorrelatedIncidentPayload payload = createSamplePayload("fingerprint-lc-" + UUID.randomUUID(), Severity.HIGH, "checkout-service");
            IncidentEntity incident = incidentService.createOrUpdateFromCorrelatedIncident(payload);
            UUID incidentId = incident.getId();

            // INVESTIGATING -> MITIGATING
            IncidentEntity mitigating = incidentService.updateStatus(incidentId, IncidentStatus.MITIGATING, "Applying connection pool expansion");
            assertThat(mitigating.getStatus()).isEqualTo(IncidentStatus.MITIGATING);

            // MITIGATING -> MONITORING
            IncidentEntity monitoring = incidentService.updateStatus(incidentId, IncidentStatus.MONITORING, "Observing latency metrics post-fix");
            assertThat(monitoring.getStatus()).isEqualTo(IncidentStatus.MONITORING);

            // MONITORING -> RESOLVED
            IncidentEntity resolved = incidentService.updateStatus(incidentId, IncidentStatus.RESOLVED, "Latency returned to normal baseline");
            assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
            assertThat(resolved.getResolvedAt()).isNotNull();
            assertThat(resolved.getResolutionNotes()).contains("normal baseline");

            // RESOLVED -> CLOSED
            IncidentEntity closed = incidentService.updateStatus(incidentId, IncidentStatus.CLOSED, "Postmortem completed");
            assertThat(closed.getStatus()).isEqualTo(IncidentStatus.CLOSED);
            assertThat(closed.getClosedAt()).isNotNull();

            // Guard check: CLOSED cannot skip to RESOLVED without reopening
            assertThatThrownBy(() -> incidentService.updateStatus(incidentId, IncidentStatus.RESOLVED, "Illegal jump"))
                    .isInstanceOf(InvalidLifecycleTransitionException.class);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Human-in-the-loop: Verification of root-cause candidate persists feedback and timeline event (PRD §19.3, §57)")
    void testHumanCandidateVerificationAndFeedback() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-candidate-verify"));
        try {
            CorrelatedIncidentPayload payload = createSamplePayload("fingerprint-cand-" + UUID.randomUUID(), Severity.HIGH, "checkout-service");
            IncidentEntity incident = incidentService.createOrUpdateFromCorrelatedIncident(payload);

            InvestigationEntity investigation = new InvestigationEntity(tenant.getId(), incident.getId());
            investigation = investigationRepository.save(investigation);

            RootCauseCandidateEntity candidate = new RootCauseCandidateEntity(
                    tenant.getId(),
                    incident.getId(),
                    investigation.getId(),
                    1,
                    "HikariCP connection pool exhaustion in checkout-service v2.8",
                    "checkout-service",
                    0.92,
                    "Direct correlation between deployment and DB connection pool saturation."
            );
            candidate = rootCauseCandidateRepository.save(candidate);

            // Create real user for feedback foreign key constraint
            UserEntity engineer = new UserEntity(
                    tenant.getId(), "daniel@checkout.com", "Daniel SRE", Role.SRE, "hashed_pwd"
            );
            engineer = userRepository.save(engineer);
            TenantContextHolder.setContext(TenantContext.ofUser(tenant.getId(), engineer.getId(), Role.SRE, "trace-verify"));

            // Engineer verifies the candidate
            RootCauseCandidateEntity verified = incidentService.verifyCandidate(
                    incident.getId(),
                    candidate.getId(),
                    VerificationStatus.VERIFIED,
                    "Verified by Daniel: Pool size bumped to 50 resolved the errors.",
                    5
            );

            assertThat(verified.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(verified.getVerificationNotes()).contains("Pool size bumped");
            assertThat(verified.getVerifiedAt()).isNotNull();

            // Verify feedback entry recorded for AI offline evaluation dataset
            List<FeedbackEntity> feedbackList = feedbackRepository.findByIncidentIdAndTenantIdOrderByCreatedAtDesc(incident.getId(), tenant.getId());
            assertThat(feedbackList).isNotEmpty();
            assertThat(feedbackList.get(0).getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(feedbackList.get(0).getRating()).isEqualTo(5);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Human comment appends sanitized COMMENT event to timeline (PRD §20)")
    void testAddComment() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-comment"));
        try {
            CorrelatedIncidentPayload payload = createSamplePayload("fingerprint-comm-" + UUID.randomUUID(), Severity.MEDIUM, "checkout-service");
            IncidentEntity incident = incidentService.createOrUpdateFromCorrelatedIncident(payload);

            IncidentEventEntity commentEvent = incidentService.addComment(
                    incident.getId(),
                    "Checking DB replica lag and CPU utilization on checkout cluster."
            );

            assertThat(commentEvent.getId()).isNotNull();
            assertThat(commentEvent.getEventType()).isEqualTo(IncidentEventType.HUMAN_COMMENT);
            assertThat(commentEvent.getSummary()).contains("replica lag");

            List<IncidentEventEntity> timeline = incidentService.getTimeline(incident.getId());
            assertThat(timeline).anyMatch(e -> e.getId().equals(commentEvent.getId()));
        } finally {
            TenantContextHolder.clear();
        }
    }

    private CorrelatedIncidentPayload createSamplePayload(String fingerprint, Severity severity, String service) {
        BlastRadiusResult blast = new BlastRadiusResult(
                service,
                Set.of("api-gateway"),
                1,
                1,
                20.0
        );

        CandidateOriginScore origin = new CandidateOriginScore(
                service,
                0.85,
                0.90,
                0.80,
                0.85,
                Instant.now()
        );

        CorrelationSignalBreakdown breakdown = CorrelationSignalBreakdown.compute(
                0.85, 0.90, 0.80, 0.75, 0.70, 0.60, 0.50, 0.40
        );

        return new CorrelatedIncidentPayload(
                UUID.randomUUID(),
                fingerprint,
                "Incident title for " + service,
                service,
                severity,
                "DETECTED",
                UUID.randomUUID(),
                Collections.emptyList(),
                ConfidenceTier.CONFIRMED_RELATIONSHIP,
                0.85,
                breakdown,
                blast,
                List.of(origin),
                Instant.now()
        );
    }
}
