package com.resolveiq.backend.notifications;

import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.notifications.security.NotificationRateLimiter;
import com.resolveiq.backend.notifications.service.NotificationService;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.repository.NotificationChannelRepository;
import com.resolveiq.backend.repository.NotificationRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import com.resolveiq.common.notification.NotificationChannelType;
import com.resolveiq.common.notification.NotificationDeliveryStatus;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationDeduplicationAndRateLimiterTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationChannelRepository channelRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private NotificationRateLimiter rateLimiter;

    private OrganizationEntity testTenant;
    private ProjectEntity testProject;
    private IncidentEntity testIncident;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        channelRepository.deleteAll();

        String runId = UUID.randomUUID().toString().substring(0, 8);
        testTenant = tenantService.createOrganization("DedupTestOrg-" + runId, "dedup-" + runId, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(testTenant.getId(), "test-setup"));
        try {
            testProject = tenantService.createProject("Project-" + runId, "proj-" + runId, "Test Project");

            testIncident = new IncidentEntity(
                    testTenant.getId(),
                    testProject.getId(),
                    "fp-" + UUID.randomUUID(),
                    "Incident Dedup Test",
                    "auth-service",
                    IncidentSeverity.SEV2,
                    "Auth Team"
            );
            testIncident.setStatus(IncidentStatus.DETECTED);
            testIncident = incidentRepository.save(testIncident);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Idempotent delivery suppresses duplicate notifications for the same state transition")
    void testStateTransitionDeduplication() {
        TenantContextHolder.setContext(TenantContext.ofSystem(testTenant.getId(), "test-run"));
        try {
            NotificationChannelEntity channel = new NotificationChannelEntity(
                    testTenant.getId(), "Alerts Slack", NotificationChannelType.SLACK,
                    "https://hooks.slack.com/mock-slack/T00/B00/X00", null, IncidentSeverity.SEV3
            );
            channel = channelRepository.save(channel);

            UUID incidentId = testIncident.getId();

            // First dispatch: should deliver
            List<NotificationEntity> first = notificationService.notifyIncidentEvent(
                    testTenant.getId(), incidentId, "INCIDENT_INVESTIGATING",
                    "Auth Service 500 error spike", "Token validation failure",
                    IncidentSeverity.SEV2, "auth-service", IncidentStatus.INVESTIGATING.name()
            );

            assertThat(first).hasSize(1);
            assertThat(first.get(0).getStatus()).isEqualTo(NotificationDeliveryStatus.SENT);

            // Second dispatch with same status transition: should deduplicate and skip
            List<NotificationEntity> duplicate = notificationService.notifyIncidentEvent(
                    testTenant.getId(), incidentId, "INCIDENT_INVESTIGATING",
                    "Auth Service 500 error spike", "Token validation failure",
                    IncidentSeverity.SEV2, "auth-service", IncidentStatus.INVESTIGATING.name()
            );

            // Deduplication suppresses dispatch
            assertThat(duplicate).isEmpty();

            // Verify only 1 record in database
            long count = notificationRepository.count();
            assertThat(count).isEqualTo(1);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("New state transition on the same incident generates a distinct notification")
    void testNewStateTransitionSendsNotification() {
        TenantContextHolder.setContext(TenantContext.ofSystem(testTenant.getId(), "test-run"));
        try {
            NotificationChannelEntity channel = new NotificationChannelEntity(
                    testTenant.getId(), "Alerts Slack", NotificationChannelType.SLACK,
                    "https://hooks.slack.com/mock-slack/T00/B00/X00", null, IncidentSeverity.SEV3
            );
            channel = channelRepository.save(channel);

            UUID incidentId = testIncident.getId();

            // 1. Detected
            List<NotificationEntity> notif1 = notificationService.notifyIncidentEvent(
                    testTenant.getId(), incidentId, "INCIDENT_DETECTED",
                    "Payment Failure", "Gateway timeout",
                    IncidentSeverity.SEV1, "payment-service", IncidentStatus.DETECTED.name()
            );
            assertThat(notif1).hasSize(1);

            // 2. Investigating
            List<NotificationEntity> notif2 = notificationService.notifyIncidentEvent(
                    testTenant.getId(), incidentId, "INCIDENT_INVESTIGATING",
                    "Payment Failure - Investigating", "Agent investigating",
                    IncidentSeverity.SEV1, "payment-service", IncidentStatus.INVESTIGATING.name()
            );
            assertThat(notif2).hasSize(1);
            assertThat(notif2.get(0).getId()).isNotEqualTo(notif1.get(0).getId());

            // 3. Resolved
            List<NotificationEntity> notif3 = notificationService.notifyIncidentEvent(
                    testTenant.getId(), incidentId, "INCIDENT_RESOLVED",
                    "Payment Failure - Resolved", "Rolled back faulty deployment",
                    IncidentSeverity.SEV1, "payment-service", IncidentStatus.RESOLVED.name()
            );
            assertThat(notif3).hasSize(1);
            assertThat(notif3.get(0).getId()).isNotEqualTo(notif2.get(0).getId());

            long count = notificationRepository.count();
            assertThat(count).isEqualTo(3);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Token bucket rate limiter throttles excessive notifications per tenant")
    void testTenantRateLimiter() {
        UUID isolatedTenant = UUID.randomUUID();

        // Default limit is 100 tokens per minute
        int allowed = 0;
        for (int i = 0; i < 110; i++) {
            if (rateLimiter.tryAcquire(isolatedTenant)) {
                allowed++;
            }
        }

        // Exactly 100 requests should be allowed in the 1-minute burst
        assertThat(allowed).isEqualTo(100);

        // Immediate subsequent request must be denied
        boolean burstAllowed = rateLimiter.tryAcquire(isolatedTenant);
        assertThat(burstAllowed).isFalse();
    }
}
