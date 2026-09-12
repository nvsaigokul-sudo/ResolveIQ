package com.resolveiq.backend.domain;

import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Incident domain entity representing a production incident (PRD §19, §36.2).
 */
@Entity
@Table(name = "incidents")
public class IncidentEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status = IncidentStatus.DETECTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentSeverity severity = IncidentSeverity.SEV3;

    @Column(nullable = false)
    private String priority = "P2";

    @Column(nullable = false)
    private String title;

    @Column(name = "root_service", nullable = false)
    private String rootService;

    @Column(name = "affected_services", nullable = false)
    private String affectedServices = "[]";

    @Column(name = "owning_team")
    private String owningTeam;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "resolution_notes")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public IncidentEntity() {
    }

    public IncidentEntity(UUID tenantId, UUID projectId, String fingerprint, String title,
                          String rootService, IncidentSeverity severity, String owningTeam) {
        super(tenantId);
        this.projectId = projectId;
        this.fingerprint = fingerprint;
        this.title = title;
        this.rootService = rootService;
        this.severity = severity != null ? severity : IncidentSeverity.SEV3;
        this.status = IncidentStatus.DETECTED;
        this.owningTeam = owningTeam;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IncidentSeverity severity) {
        this.severity = severity;
        this.updatedAt = Instant.now();
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRootService() {
        return rootService;
    }

    public void setRootService(String rootService) {
        this.rootService = rootService;
    }

    public String getAffectedServices() {
        return affectedServices;
    }

    public void setAffectedServices(String affectedServices) {
        this.affectedServices = affectedServices;
    }

    public String getOwningTeam() {
        return owningTeam;
    }

    public void setOwningTeam(String owningTeam) {
        this.owningTeam = owningTeam;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.updatedAt = Instant.now();
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
        this.updatedAt = Instant.now();
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

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}
