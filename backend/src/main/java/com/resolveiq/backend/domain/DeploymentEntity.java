package com.resolveiq.backend.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Service deployment record (PRD §25.5, §36.1).
 */
@Entity
@Table(name = "deployments")
public class DeploymentEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String environment = "production";

    @Column(nullable = false)
    private String version;

    @Column(name = "commit_sha", nullable = false)
    private String commitSha;

    @Column(name = "commit_message", columnDefinition = "TEXT")
    private String commitMessage;

    @Column(name = "deployed_by", nullable = false)
    private String deployedBy;

    @Column(nullable = false)
    private String status = "SUCCESS";

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt = Instant.now();

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public DeploymentEntity() {
    }

    public DeploymentEntity(UUID tenantId, UUID projectId, UUID serviceId, String serviceName,
                            String environment, String version, String commitSha,
                            String commitMessage, String deployedBy, String status,
                            Instant deployedAt, String metadata) {
        super(tenantId);
        this.projectId = projectId;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.environment = environment != null ? environment : "production";
        this.version = version;
        this.commitSha = commitSha;
        this.commitMessage = commitMessage;
        this.deployedBy = deployedBy;
        this.status = status != null ? status : "SUCCESS";
        this.deployedAt = deployedAt != null ? deployedAt : Instant.now();
        this.metadata = metadata;
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

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getDeployedBy() {
        return deployedBy;
    }

    public void setDeployedBy(String deployedBy) {
        this.deployedBy = deployedBy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }

    public void setDeployedAt(Instant deployedAt) {
        this.deployedAt = deployedAt;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
