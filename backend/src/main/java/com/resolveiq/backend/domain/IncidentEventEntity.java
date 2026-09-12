package com.resolveiq.backend.domain;

import com.resolveiq.common.incident.IncidentEventType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable timeline event for an incident (PRD §20).
 * "No timeline event is ever updated or deleted; corrections are new events referencing the corrected one."
 */
@Entity
@Table(name = "incident_events")
public class IncidentEventEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private IncidentEventType eventType;

    @Column(name = "actor_type", nullable = false)
    private String actorType = "SYSTEM";

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "reference_event_id")
    private UUID referenceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IncidentEventEntity() {
    }

    public IncidentEventEntity(UUID tenantId, UUID incidentId, IncidentEventType eventType,
                               String actorType, UUID actorId, String summary, String payload,
                               UUID referenceEventId) {
        super(tenantId);
        this.incidentId = incidentId;
        this.eventType = eventType;
        this.actorType = actorType != null ? actorType : "SYSTEM";
        this.actorId = actorId;
        this.summary = summary;
        this.payload = payload;
        this.referenceEventId = referenceEventId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(UUID incidentId) {
        this.incidentId = incidentId;
    }

    public IncidentEventType getEventType() {
        return eventType;
    }

    public void setEventType(IncidentEventType eventType) {
        this.eventType = eventType;
    }

    public String getActorType() {
        return actorType;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public UUID getReferenceEventId() {
        return referenceEventId;
    }

    public void setReferenceEventId(UUID referenceEventId) {
        this.referenceEventId = referenceEventId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
