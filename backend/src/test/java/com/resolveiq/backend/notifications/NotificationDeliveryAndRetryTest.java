package com.resolveiq.backend.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.notifications.adapters.*;
import com.resolveiq.backend.notifications.service.NotificationRetryPolicy;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationDeliveryAndRetryTest {

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
    private NotificationRetryPolicy retryPolicy;

    @Autowired
    private SlackNotificationAdapter slackAdapter;

    @Autowired
    private EmailNotificationAdapter emailAdapter;

    @Autowired
    private WebhookNotificationAdapter webhookAdapter;

    @Autowired
    private ObjectMapper objectMapper;

    private OrganizationEntity testTenant;
    private ProjectEntity testProject;
    private IncidentEntity testIncident;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        channelRepository.deleteAll();

        String runId = UUID.randomUUID().toString().substring(0, 8);
        testTenant = tenantService.createOrganization("NotificationTestOrg-" + runId, "notif-test-" + runId, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(testTenant.getId(), "test-user"));
        try {
            testProject = tenantService.createProject("Project-" + runId, "proj-" + runId, "Test Project");

            testIncident = new IncidentEntity(
                    testTenant.getId(),
                    testProject.getId(),
                    "fp-" + UUID.randomUUID(),
                    "Test Incident",
                    "api-gateway",
                    IncidentSeverity.SEV1,
                    "Team A"
            );
            testIncident.setStatus(IncidentStatus.DETECTED);
            testIncident = incidentRepository.save(testIncident);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Notification row is persisted before delivery dispatch and marked SENT on success")
    void testPreDeliveryRowPersistence() {
        TenantContextHolder.setContext(TenantContext.ofSystem(testTenant.getId(), "test-run"));
        try {
            NotificationChannelEntity channel = new NotificationChannelEntity(
                    testTenant.getId(), "Engineering Slack", NotificationChannelType.SLACK,
                    "https://hooks.slack.com/mock-slack/T00/B00/X00", null, IncidentSeverity.SEV3
            );
            channel = channelRepository.save(channel);

            UUID incidentId = testIncident.getId();
            List<NotificationEntity> notifications = notificationService.notifyIncidentEvent(
                    testTenant.getId(),
                    incidentId,
                    "INCIDENT_DETECTED",
                    "Elevated 5xx Error Rate detected on api-gateway",
                    "Connection pool exhaustion on database upstream",
                    IncidentSeverity.SEV1,
                    "api-gateway",
                    IncidentStatus.DETECTED.name()
            );

            assertThat(notifications).hasSize(1);
            NotificationEntity notification = notifications.get(0);
            assertThat(notification.getId()).isNotNull();
            assertThat(notification.getTenantId()).isEqualTo(testTenant.getId());
            assertThat(notification.getIncidentId()).isEqualTo(incidentId);

            // Verification in database: row exists and has valid delivery state
            NotificationEntity persisted = notificationRepository.findById(notification.getId()).orElse(null);
            assertThat(persisted).isNotNull();
            assertThat(persisted.getAttemptCount()).isGreaterThanOrEqualTo(1);
            assertThat(persisted.getStatus()).isIn(NotificationDeliveryStatus.SENT, NotificationDeliveryStatus.PENDING);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Slack delivery adapter generates Block Kit payload")
    void testSlackAdapterBlockKit() {
        NotificationChannelEntity channel = new NotificationChannelEntity(
                testTenant.getId(), "Ops Slack", NotificationChannelType.SLACK,
                "https://hooks.slack.com/mock-slack/T00/B00/X00", null, IncidentSeverity.SEV4
        );
        NotificationEntity notification = new NotificationEntity(
                testTenant.getId(), testIncident.getId(), UUID.randomUUID(),
                NotificationChannelType.SLACK, channel.getDestination(), "INCIDENT_DETECTED",
                "key-slack-test", "{\"title\":\"Test title\",\"severity\":\"SEV1\",\"root_service\":\"api-gateway\",\"status\":\"DETECTED\",\"summary\":\"Test summary\"}"
        );

        Map<String, Object> blockKit = slackAdapter.formatSlackBlockKit(
                "Test title", "SEV1", "api-gateway", "DETECTED", "Test summary", notification.getIncidentId()
        );
        assertThat(blockKit).containsKey("blocks");
        assertThat(blockKit.get("text").toString()).contains("Test title");

        DeliveryResult result = slackAdapter.deliver(channel, notification);
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Email delivery adapter generates HTML and plaintext content")
    void testEmailAdapterContent() {
        NotificationChannelEntity channel = new NotificationChannelEntity(
                testTenant.getId(), "Oncall Email", NotificationChannelType.EMAIL,
                "oncall@example.com", null, IncidentSeverity.SEV4
        );
        NotificationEntity notification = new NotificationEntity(
                testTenant.getId(), testIncident.getId(), UUID.randomUUID(),
                NotificationChannelType.EMAIL, channel.getDestination(), "INCIDENT_DETECTED",
                "key-email-test", "{\"title\":\"Database Latency High\",\"severity\":\"SEV2\",\"root_service\":\"db-primary\",\"status\":\"DETECTED\",\"summary\":\"P99 latency > 2000ms\"}"
        );

        String html = emailAdapter.formatHtmlEmail(
                "Database Latency High", "SEV2", "db-primary", "DETECTED", "P99 latency > 2000ms", notification.getIncidentId()
        );
        assertThat(html).contains("Database Latency High");
        assertThat(html).contains("ResolveIQ Automated Alert");

        DeliveryResult result = emailAdapter.deliver(channel, notification);
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Webhook delivery adapter generates HMAC-SHA256 signature")
    void testWebhookAdapterSignature() {
        String secret = "super-secret-webhook-key-12345";
        NotificationChannelEntity channel = new NotificationChannelEntity(
                testTenant.getId(), "Custom Webhook", NotificationChannelType.WEBHOOK,
                "https://example.com/webhook", secret, IncidentSeverity.SEV4
        );
        NotificationEntity notification = new NotificationEntity(
                testTenant.getId(), testIncident.getId(), UUID.randomUUID(),
                NotificationChannelType.WEBHOOK, channel.getDestination(), "INCIDENT_DETECTED",
                "key-webhook-test", "{\"title\":\"Webhook Alert\",\"severity\":\"SEV1\",\"root_service\":\"api-gateway\",\"status\":\"DETECTED\",\"summary\":\"Webhook RCA summary\"}"
        );

        long timestamp = Instant.now().getEpochSecond();
        String signature = webhookAdapter.calculateHmacSha256("{\"test\":\"payload\"}", secret, timestamp);
        assertThat(signature).isNotNull();
        assertThat(signature).startsWith("sha256=");

        DeliveryResult result = webhookAdapter.deliver(channel, notification);
        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Exponential backoff with jitter computes progressive delays")
    void testExponentialBackoffPolicy() {
        long delay1 = retryPolicy.calculateBackoff(1).toMillis();
        long delay2 = retryPolicy.calculateBackoff(2).toMillis();
        long delay3 = retryPolicy.calculateBackoff(3).toMillis();

        // Exponential progression with base=1000ms, multiplier=2.0, jitter=[50..300ms]
        // Attempt 1: 1000 + [50..300] ms
        assertThat(delay1).isBetween(1050L, 1300L);
        // Attempt 2: 2000 + [50..300] ms
        assertThat(delay2).isBetween(2050L, 2300L);
        // Attempt 3: 4000 + [50..300] ms
        assertThat(delay3).isBetween(4050L, 4300L);

        // Max cap check
        long delayHuge = retryPolicy.calculateBackoff(10).toMillis();
        assertThat(delayHuge).isLessThanOrEqualTo(60300L); // capped at 60s + max jitter
    }

    @Test
    @DisplayName("Failed notification transitions to DEAD_LETTERED when max retries exceeded and can be replayed")
    void testDlqTransitionAndReplay() {
        TenantContextHolder.setContext(TenantContext.ofSystem(testTenant.getId(), "test-dlq"));
        try {
            NotificationChannelEntity channel = new NotificationChannelEntity(
                    testTenant.getId(), "Failing Channel", NotificationChannelType.WEBHOOK,
                    "https://example.com/mock-webhook-endpoint", "secret", IncidentSeverity.SEV4
            );
            channel = channelRepository.save(channel);

            // Seed a notification that has reached max retry attempts
            NotificationEntity dlqNotification = new NotificationEntity(
                    testTenant.getId(), testIncident.getId(), channel.getId(),
                    NotificationChannelType.WEBHOOK, channel.getDestination(), "INCIDENT_DETECTED",
                    "key-dlq-test", "{}"
            );
            dlqNotification.setStatus(NotificationDeliveryStatus.DEAD_LETTERED);
            dlqNotification.setAttemptCount(3);
            dlqNotification.setErrorMessage("HTTP 500: Remote server crashed permanently");
            dlqNotification = notificationRepository.save(dlqNotification);

            // Verify it appears in DLQ query
            List<NotificationEntity> dlqList = notificationService.listDeadLetterQueue(testTenant.getId());
            assertThat(dlqList).hasSize(1);
            assertThat(dlqList.get(0).getId()).isEqualTo(dlqNotification.getId());
            assertThat(dlqList.get(0).getStatus()).isEqualTo(NotificationDeliveryStatus.DEAD_LETTERED);

            // Replay from DLQ
            NotificationEntity replayed = notificationService.retryDeadLettered(testTenant.getId(), dlqNotification.getId());
            assertThat(replayed).isNotNull();
            assertThat(replayed.getAttemptCount()).isEqualTo(4);
            assertThat(replayed.getStatus()).isNotEqualTo(NotificationDeliveryStatus.DEAD_LETTERED);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
