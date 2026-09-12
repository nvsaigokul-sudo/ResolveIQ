package com.resolveiq.backend.notifications.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.NotificationChannelEntity;
import com.resolveiq.backend.domain.NotificationEntity;
import com.resolveiq.backend.notifications.adapters.DeliveryResult;
import com.resolveiq.backend.notifications.adapters.NotificationAdapterRegistry;
import com.resolveiq.backend.notifications.adapters.NotificationDeliveryAdapter;
import com.resolveiq.backend.notifications.kafka.NotificationKafkaProducer;
import com.resolveiq.backend.notifications.security.NotificationRateLimiter;
import com.resolveiq.backend.notifications.security.SsrfValidator;
import com.resolveiq.backend.repository.NotificationChannelRepository;
import com.resolveiq.backend.repository.NotificationRepository;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.notification.NotificationChannelType;
import com.resolveiq.common.notification.NotificationDeliveredPayload;
import com.resolveiq.common.notification.NotificationDeliveryStatus;
import com.resolveiq.common.notification.NotificationFailedPayload;
import com.resolveiq.common.notification.NotificationRequestedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Production-grade notification service with delivery tracking, retries with backoff,
 * dead-letter queueing, and deduplication (PRD §27, §35, §51, §52).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationChannelRepository channelRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationAdapterRegistry adapterRegistry;
    private final NotificationRetryPolicy retryPolicy;
    private final NotificationRateLimiter rateLimiter;
    private final SsrfValidator ssrfValidator;
    private final NotificationKafkaProducer kafkaProducer;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationChannelRepository channelRepository,
            NotificationRepository notificationRepository,
            NotificationAdapterRegistry adapterRegistry,
            NotificationRetryPolicy retryPolicy,
            NotificationRateLimiter rateLimiter,
            SsrfValidator ssrfValidator,
            NotificationKafkaProducer kafkaProducer,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.notificationRepository = notificationRepository;
        this.adapterRegistry = adapterRegistry;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
        this.ssrfValidator = ssrfValidator;
        this.kafkaProducer = kafkaProducer;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    // ========================================================================
    // 1. Channel Configuration (PRD §27, §35)
    // ========================================================================

    @Transactional
    public NotificationChannelEntity createChannel(
            UUID tenantId,
            String name,
            NotificationChannelType channelType,
            String destination,
            String secretToken,
            IncidentSeverity minSeverity) {

        if (name == null || name.isBlank()) {
            throw new ValidationException("Channel name cannot be blank");
        }
        if (destination == null || destination.isBlank()) {
            throw new ValidationException("Channel destination cannot be blank");
        }

        // SSRF validation on Webhook and Slack channel destinations
        if (channelType == NotificationChannelType.WEBHOOK || channelType == NotificationChannelType.SLACK) {
            ssrfValidator.validateUrl(destination);
        }

        NotificationChannelEntity channel = new NotificationChannelEntity(
                tenantId,
                name.trim(),
                channelType,
                destination.trim(),
                secretToken,
                minSeverity != null ? minSeverity : IncidentSeverity.SEV3
        );

        NotificationChannelEntity saved = channelRepository.save(channel);

        auditLogService.recordCurrentContext(
                "NOTIFICATION_CHANNEL_CREATED",
                "channel:" + saved.getId(),
                null,
                String.format("type:%s, name:%s", channelType, saved.getName())
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelEntity> listChannels(UUID tenantId) {
        return channelRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public NotificationChannelEntity getChannel(UUID tenantId, UUID channelId) {
        return channelRepository.findByIdAndTenantId(channelId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification channel not found: " + channelId));
    }

    @Transactional
    public void deleteChannel(UUID tenantId, UUID channelId) {
        NotificationChannelEntity channel = channelRepository.findByIdAndTenantId(channelId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification channel not found: " + channelId));

        channelRepository.delete(channel);

        auditLogService.recordCurrentContext(
                "NOTIFICATION_CHANNEL_DELETED",
                "channel:" + channelId,
                "type:" + channel.getChannelType(),
                null
        );
    }

    // ========================================================================
    // 2. Incident Event Notification Dispatch (PRD §27)
    // ========================================================================

    @Transactional
    public List<NotificationEntity> notifyIncidentEvent(
            UUID tenantId,
            UUID incidentId,
            String eventType,
            String title,
            String summary,
            IncidentSeverity severity,
            String rootService,
            String status) {

        // Rate limiting check
        if (!rateLimiter.tryAcquire(tenantId)) {
            log.warn("Rate limit exceeded for tenant {} notifications. Throttling notification.", tenantId);
            return Collections.emptyList();
        }

        List<NotificationChannelEntity> activeChannels = channelRepository.findByTenantIdAndEnabledTrue(tenantId);
        List<NotificationEntity> createdNotifications = new ArrayList<>();

        for (NotificationChannelEntity channel : activeChannels) {
            if (!isSeveritySufficient(severity, channel.getMinSeverity())) {
                continue;
            }

            // Deduplication invariant (PRD §27): Don't re-notify for the same state transition twice
            String idempotencyKey = String.format("%s:%s:%s:%s:%s",
                    tenantId, incidentId, channel.getId(), eventType, status);

            if (notificationRepository.existsByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)) {
                log.debug("Skipping duplicate notification for idempotency key: {}", idempotencyKey);
                continue;
            }

            // Prepare payload
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("incident_id", incidentId != null ? incidentId.toString() : null);
            payloadMap.put("event_type", eventType);
            payloadMap.put("title", title);
            payloadMap.put("summary", summary);
            payloadMap.put("severity", severity != null ? severity.name() : "SEV3");
            payloadMap.put("root_service", rootService);
            payloadMap.put("status", status);
            payloadMap.put("timestamp", Instant.now().toString());

            String payloadJson;
            try {
                payloadJson = objectMapper.writeValueAsString(payloadMap);
            } catch (JsonProcessingException e) {
                payloadJson = "{}";
            }

            // PRD §27 Invariant: Persist status row BEFORE attempting delivery!
            NotificationEntity notification = new NotificationEntity(
                    tenantId,
                    incidentId,
                    channel.getId(),
                    channel.getChannelType(),
                    channel.getDestination(),
                    eventType,
                    idempotencyKey,
                    payloadJson
            );
            notification = notificationRepository.save(notification);

            // Publish NotificationRequested to Kafka
            kafkaProducer.publishNotificationRequested(new NotificationRequestedPayload(
                    notification.getId(),
                    tenantId,
                    incidentId,
                    channel.getId(),
                    channel.getChannelType(),
                    channel.getDestination(),
                    eventType,
                    idempotencyKey,
                    severity != null ? severity.name() : "SEV3",
                    title,
                    summary,
                    payloadMap,
                    notification.getCreatedAt()
            ));

            // Execute delivery
            executeDelivery(channel, notification);
            createdNotifications.add(notification);
        }

        return createdNotifications;
    }

    // ========================================================================
    // 3. Delivery Execution, Retry & DLQ Progression (PRD §27)
    // ========================================================================

    @Transactional
    public NotificationEntity executeDelivery(NotificationChannelEntity channel, NotificationEntity notification) {
        notification.setAttemptCount(notification.getAttemptCount() + 1);
        notification.setUpdatedAt(Instant.now());

        NotificationDeliveryAdapter adapter = adapterRegistry.getAdapter(notification.getChannelType());
        DeliveryResult result = adapter.deliver(channel, notification);

        if (result.success()) {
            notification.setStatus(NotificationDeliveryStatus.SENT);
            notification.setDeliveredAt(Instant.now());
            notification.setErrorMessage(null);
            NotificationEntity saved = notificationRepository.save(notification);

            kafkaProducer.publishNotificationDelivered(new NotificationDeliveredPayload(
                    saved.getId(),
                    saved.getTenantId(),
                    saved.getIncidentId(),
                    channel.getId(),
                    saved.getChannelType(),
                    saved.getDestination(),
                    saved.getAttemptCount(),
                    saved.getDeliveredAt()
            ));

            return saved;
        } else {
            // Failure handling with exponential backoff & DLQ (PRD §27)
            if (notification.getAttemptCount() < notification.getMaxAttempts()) {
                Duration backoff = retryPolicy.calculateBackoff(notification.getAttemptCount());
                notification.setStatus(NotificationDeliveryStatus.RETRYING);
                notification.setNextRetryAt(Instant.now().plus(backoff));
                notification.setErrorMessage(result.errorMessage());
                log.warn("Notification {} delivery failed (attempt {}/{}). Retrying in {} ms: {}",
                        notification.getId(), notification.getAttemptCount(), notification.getMaxAttempts(),
                        backoff.toMillis(), result.errorMessage());
            } else {
                notification.setStatus(NotificationDeliveryStatus.DEAD_LETTERED);
                notification.setNextRetryAt(null);
                notification.setErrorMessage(result.errorMessage());
                log.error("Notification {} delivery exhausted max attempts ({}). Moved to DEAD_LETTERED: {}",
                        notification.getId(), notification.getMaxAttempts(), result.errorMessage());

                kafkaProducer.publishNotificationFailed(new NotificationFailedPayload(
                        notification.getId(),
                        notification.getTenantId(),
                        notification.getIncidentId(),
                        channel.getId(),
                        notification.getChannelType(),
                        notification.getDestination(),
                        notification.getAttemptCount(),
                        notification.getStatus(),
                        notification.getErrorMessage(),
                        Instant.now()
                ));
            }

            return notificationRepository.save(notification);
        }
    }

    // ========================================================================
    // 4. Dead-Letter Queue (DLQ) & Query Endpoints (PRD §27, §35)
    // ========================================================================

    @Transactional(readOnly = true)
    public List<NotificationEntity> listNotifications(UUID tenantId, NotificationDeliveryStatus status) {
        if (status != null) {
            return notificationRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
        }
        return notificationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public NotificationEntity getNotification(UUID tenantId, UUID notificationId) {
        return notificationRepository.findByIdAndTenantId(notificationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
    }

    @Transactional(readOnly = true)
    public List<NotificationEntity> listDeadLetterQueue(UUID tenantId) {
        return notificationRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(
                tenantId, NotificationDeliveryStatus.DEAD_LETTERED);
    }

    @Transactional
    public NotificationEntity retryDeadLettered(UUID tenantId, UUID notificationId) {
        NotificationEntity notification = notificationRepository.findByIdAndTenantId(notificationId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found in DLQ: " + notificationId));

        NotificationChannelEntity channel = channelRepository.findById(notification.getChannelId())
                .orElseGet(() -> new NotificationChannelEntity(
                        tenantId, "Ad-hoc Channel", notification.getChannelType(),
                        notification.getDestination(), null, IncidentSeverity.SEV4));

        notification.setStatus(NotificationDeliveryStatus.RETRYING);
        NotificationEntity savedNotification = notificationRepository.save(notification);

        auditLogService.recordCurrentContext(
                "NOTIFICATION_DLQ_RETRY_REQUESTED",
                "notification:" + notificationId,
                "status:DEAD_LETTERED",
                "status:RETRYING"
        );

        return executeDelivery(channel, savedNotification);
    }

    private boolean isSeveritySufficient(IncidentSeverity incidentSeverity, IncidentSeverity minSeverity) {
        if (incidentSeverity == null || minSeverity == null) {
            return true;
        }
        // SEV1 > SEV2 > SEV3 > SEV4
        return incidentSeverity.ordinal() <= minSeverity.ordinal();
    }
}
