package com.resolveiq.backend.chaos;

import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.backend.domain.NotificationChannelEntity;
import com.resolveiq.backend.domain.NotificationEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.notifications.adapters.DeliveryResult;
import com.resolveiq.backend.notifications.adapters.WebhookNotificationAdapter;
import com.resolveiq.backend.notifications.service.NotificationRetryPolicy;
import com.resolveiq.backend.notifications.service.NotificationService;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.repository.NotificationChannelRepository;
import com.resolveiq.backend.repository.NotificationRepository;
import com.resolveiq.backend.repository.ProjectRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.notification.NotificationChannelType;
import com.resolveiq.common.notification.NotificationDeliveryStatus;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Chaos & Fault Injection: Outbound Webhook Failures, Exponential Backoff, and Dead-Letter Queue (PRD §§27, 34).
 */
@SpringBootTest
@ActiveProfiles("test")
public class NotificationChaosAndDlqTest {

    @Autowired private TenantService tenantService;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationChannelRepository channelRepository;
    @Autowired private NotificationRetryPolicy retryPolicy;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private ProjectRepository projectRepository;

    @MockBean private WebhookNotificationAdapter mockAdapter;

    private OrganizationEntity tenant;
    private NotificationChannelEntity webhookChannel;
    private IncidentEntity testIncident;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("Notif Chaos Org " + suffix, "nchaos-" + suffix, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "notif-setup"));
        try {
            ProjectEntity project = projectRepository.save(new ProjectEntity(tenant.getId(), "Notif Proj", "notif-proj-" + suffix, "desc"));
            testIncident = new IncidentEntity(
                    tenant.getId(),
                    project.getId(),
                    "fp-notif-" + suffix,
                    "Notif Test Incident",
                    "payment-service",
                    IncidentSeverity.SEV1,
                    "Payments"
            );
            testIncident = incidentRepository.save(testIncident);
        } finally {
            TenantContextHolder.clear();
        }

        when(mockAdapter.supports(NotificationChannelType.WEBHOOK)).thenReturn(true);

        webhookChannel = notificationService.createChannel(
                tenant.getId(),
                "Opsgenie Webhook " + suffix,
                NotificationChannelType.WEBHOOK,
                "https://api.opsgenie.com/v1/json/resolveiq",
                "secret-token-123",
                IncidentSeverity.SEV2
        );
    }

    @Test
    @DisplayName("Notification Chaos 1: Exponential Backoff Calculation Increases with Jitter")
    void testExponentialBackoffCalculation() {
        Duration backoff1 = retryPolicy.calculateBackoff(1);
        Duration backoff2 = retryPolicy.calculateBackoff(2);
        Duration backoff3 = retryPolicy.calculateBackoff(3);

        assertThat(backoff1.toMillis()).isGreaterThanOrEqualTo(1000L);
        assertThat(backoff2.toMillis()).isGreaterThan(backoff1.toMillis());
        assertThat(backoff3.toMillis()).isGreaterThan(backoff2.toMillis());
    }

    @Test
    @DisplayName("Notification Chaos 2: Remote Endpoint 503 Outage Triggers Retry Progression and Eventual DLQ")
    void testRemoteOutageTriggersDlq() {
        when(mockAdapter.deliver(any(), any()))
                .thenReturn(DeliveryResult.failure(503, "HTTP 503 Service Unavailable: Gateway Outage", 150L));

        List<NotificationEntity> notifications = notificationService.notifyIncidentEvent(
                tenant.getId(),
                testIncident.getId(),
                "INCIDENT_CREATED",
                "Chaos Outage Alert",
                "Summary of chaos alert",
                IncidentSeverity.SEV1,
                "auth-service",
                "INVESTIGATING"
        );

        assertThat(notifications).hasSize(1);
        NotificationEntity n = notifications.get(0);
        // First attempt failed -> Status is RETRYING with exponential backoff scheduled
        assertThat(n.getStatus()).isEqualTo(NotificationDeliveryStatus.RETRYING);
        assertThat(n.getAttemptCount()).isEqualTo(1);
        assertThat(n.getErrorMessage()).contains("HTTP 503 Service Unavailable");

        // Simulate subsequent retries up to maximum limit (attempt 2)
        n = notificationService.executeDelivery(webhookChannel, n);
        assertThat(n.getAttemptCount()).isEqualTo(2);
        assertThat(n.getStatus()).isEqualTo(NotificationDeliveryStatus.RETRYING);

        // Attempt 3: Exhausts maxAttempts (3) -> Status transitions to DEAD_LETTERED
        n = notificationService.executeDelivery(webhookChannel, n);
        assertThat(n.getAttemptCount()).isEqualTo(3);
        assertThat(n.getStatus()).isEqualTo(NotificationDeliveryStatus.DEAD_LETTERED);
    }

    @Test
    @DisplayName("Notification Chaos 3: Duplicate Incident Event State Deduplicated via Idempotency Key")
    void testDuplicateNotificationDeduplication() {
        when(mockAdapter.deliver(any(), any()))
                .thenReturn(DeliveryResult.success(200, "ext-msg-123", 25L));

        // First event
        List<NotificationEntity> batch1 = notificationService.notifyIncidentEvent(
                tenant.getId(),
                testIncident.getId(),
                "INCIDENT_RESOLVED",
                "Resolved Incident",
                "All services green",
                IncidentSeverity.SEV1,
                "auth-service",
                "RESOLVED"
        );
        assertThat(batch1).hasSize(1);

        // Immediate duplicate event for exact same state transition
        List<NotificationEntity> batch2 = notificationService.notifyIncidentEvent(
                tenant.getId(),
                testIncident.getId(),
                "INCIDENT_RESOLVED",
                "Resolved Incident",
                "All services green",
                IncidentSeverity.SEV1,
                "auth-service",
                "RESOLVED"
        );

        assertThat(batch2).isEmpty();
    }
}
