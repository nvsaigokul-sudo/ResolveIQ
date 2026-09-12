package com.resolveiq.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "environments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_environments_tenant_project_name", columnNames = {"tenant_id", "project_id", "name"})
})
public class EnvironmentEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_production", nullable = false)
    private boolean isProduction = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EnvironmentEntity() {
    }

    public EnvironmentEntity(UUID tenantId, UUID projectId, String name, boolean isProduction) {
        super(tenantId);
        this.projectId = projectId;
        this.name = name;
        this.isProduction = isProduction;
        this.createdAt = Instant.now();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isProduction() {
        return isProduction;
    }

    public void setProduction(boolean production) {
        isProduction = production;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
