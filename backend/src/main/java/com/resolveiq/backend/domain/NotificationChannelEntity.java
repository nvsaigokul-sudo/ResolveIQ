package com.resolveiq.backend.domain;

import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.notification.NotificationChannelType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-scoped notification channel configuration (PRD §27, §35).
 */
@Entity
@Table(name = "notification_channels")
public class NotificationChannelEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 32)
    private NotificationChannelType channelType;

    @Column(nullable = false, length = 1024)
    private String destination;

    @Column(name = "secret_token")
    private String secretToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_severity", nullable = false, length = 32)
    private IncidentSeverity minSeverity = IncidentSeverity.SEV3;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public NotificationChannelEntity() {
    }

    public NotificationChannelEntity(UUID tenantId, String name, NotificationChannelType channelType,
                                   String destination, String secretToken, IncidentSeverity minSeverity) {
        super(tenantId);
        this.name = name;
        this.channelType = channelType;
        this.destination = destination;
        this.secretToken = secretToken;
        this.minSeverity = minSeverity != null ? minSeverity : IncidentSeverity.SEV3;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public NotificationChannelType getChannelType() {
        return channelType;
    }

    public void setChannelType(NotificationChannelType channelType) {
        this.channelType = channelType;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }

    public IncidentSeverity getMinSeverity() {
        return minSeverity;
    }

    public void setMinSeverity(IncidentSeverity minSeverity) {
        this.minSeverity = minSeverity;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
