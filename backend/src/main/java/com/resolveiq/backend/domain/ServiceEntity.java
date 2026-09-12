package com.resolveiq.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "services", uniqueConstraints = {
        @UniqueConstraint(name = "uq_services_tenant_project_name", columnNames = {"tenant_id", "project_id", "name"})
})
public class ServiceEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String tier = "TIER_1";

    @Column(name = "owner_team", nullable = false)
    private String ownerTeam;

    @Column(name = "repo_url")
    private String repoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ServiceEntity() {
    }

    public ServiceEntity(UUID tenantId, UUID projectId, String name, String tier, String ownerTeam, String repoUrl) {
        super(tenantId);
        this.projectId = projectId;
        this.name = name;
        this.tier = tier != null ? tier : "TIER_1";
        this.ownerTeam = ownerTeam;
        this.repoUrl = repoUrl;
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

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getOwnerTeam() {
        return ownerTeam;
    }

    public void setOwnerTeam(String ownerTeam) {
        this.ownerTeam = ownerTeam;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
