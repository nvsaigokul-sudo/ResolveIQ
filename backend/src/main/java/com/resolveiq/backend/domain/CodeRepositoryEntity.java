package com.resolveiq.backend.domain;

import com.resolveiq.common.integration.GitProviderType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-scoped authorized git repository connection (PRD §26).
 */
@Entity
@Table(name = "code_repositories", uniqueConstraints = {
        @UniqueConstraint(name = "uq_code_repos_tenant_repo", columnNames = {"tenant_id", "provider", "repo_name"})
})
public class CodeRepositoryEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GitProviderType provider;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "repo_url", nullable = false, length = 512)
    private String repoUrl;

    @Column(name = "access_token")
    private String accessToken;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CodeRepositoryEntity() {
    }

    public CodeRepositoryEntity(UUID tenantId, GitProviderType provider, String repoName, String repoUrl, String accessToken) {
        super(tenantId);
        this.provider = provider;
        this.repoName = repoName;
        this.repoUrl = repoUrl;
        this.accessToken = accessToken;
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

    public GitProviderType getProvider() {
        return provider;
    }

    public void setProvider(GitProviderType provider) {
        this.provider = provider;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
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
