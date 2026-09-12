package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.NotificationChannelEntity;
import com.resolveiq.backend.domain.NotificationEntity;
import com.resolveiq.backend.notifications.service.NotificationService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ForbiddenException;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.notification.NotificationChannelType;
import com.resolveiq.common.notification.NotificationDeliveryStatus;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Notification Channels, Delivery Tracking, and DLQ (PRD §27, §35).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public record CreateChannelRequest(
            @NotBlank(message = "Channel name cannot be blank") String name,
            @NotNull(message = "Channel type cannot be null") NotificationChannelType channelType,
            @NotBlank(message = "Destination cannot be blank") String destination,
            String secretToken,
            IncidentSeverity minSeverity
    ) {}

    // ========================================================================
    // Channels
    // ========================================================================

    @PostMapping("/channels")
    public ResponseEntity<ApiResponse<NotificationChannelEntity>> createChannel(
            @Valid @RequestBody CreateChannelRequest request) {
        requirePermission("configure notification channels");
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        NotificationChannelEntity channel = notificationService.createChannel(
                tenantId,
                request.name(),
                request.channelType(),
                request.destination(),
                request.secretToken(),
                request.minSeverity()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(channel));
    }

    @GetMapping("/channels")
    public ResponseEntity<ApiResponse<List<NotificationChannelEntity>>> listChannels() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<NotificationChannelEntity> channels = notificationService.listChannels(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(channels));
    }

    @GetMapping("/channels/{id}")
    public ResponseEntity<ApiResponse<NotificationChannelEntity>> getChannel(@PathVariable("id") UUID channelId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        NotificationChannelEntity channel = notificationService.getChannel(tenantId, channelId);
        return ResponseEntity.ok(ApiResponse.ok(channel));
    }

    @DeleteMapping("/channels/{id}")
    public ResponseEntity<Void> deleteChannel(@PathVariable("id") UUID channelId) {
        requirePermission("delete notification channels");
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        notificationService.deleteChannel(tenantId, channelId);
        return ResponseEntity.noContent().build();
    }

    // ========================================================================
    // Notifications History & DLQ (PRD §27)
    // ========================================================================

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationEntity>>> listNotifications(
            @RequestParam(value = "status", required = false) NotificationDeliveryStatus status) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<NotificationEntity> notifications = notificationService.listNotifications(tenantId, status);
        return ResponseEntity.ok(ApiResponse.ok(notifications));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationEntity>> getNotification(@PathVariable("id") UUID notificationId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        NotificationEntity notification = notificationService.getNotification(tenantId, notificationId);
        return ResponseEntity.ok(ApiResponse.ok(notification));
    }

    @GetMapping("/dlq")
    public ResponseEntity<ApiResponse<List<NotificationEntity>>> getDeadLetterQueue() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<NotificationEntity> dlqItems = notificationService.listDeadLetterQueue(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(dlqItems));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<NotificationEntity>> retryNotification(@PathVariable("id") UUID notificationId) {
        requirePermission("retry dead-lettered notifications");
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        NotificationEntity retried = notificationService.retryDeadLettered(tenantId, notificationId);
        return ResponseEntity.ok(ApiResponse.ok(retried));
    }

    private void requirePermission(String action) {
        Role role = TenantContextHolder.getContext().map(TenantContext::role).orElse(Role.VIEWER);
        if (!role.canManageNotifications()) {
            throw new ForbiddenException("Role " + role + " is not authorized to " + action);
        }
    }
}
