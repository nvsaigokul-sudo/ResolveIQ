package com.resolveiq.backend.integrations.git;

import com.resolveiq.backend.domain.CodeRepositoryEntity;
import com.resolveiq.backend.repository.CodeRepositoryRepository;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.common.exception.ForbiddenException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.integration.GitProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service managing read-only, opt-in git integrations with structural allowlist enforcement (PRD §26).
 */
@Service
public class GitIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(GitIntegrationService.class);

    private final CodeRepositoryRepository codeRepositoryRepository;
    private final GitClient gitClient;
    private final AuditLogService auditLogService;

    public GitIntegrationService(
            CodeRepositoryRepository codeRepositoryRepository,
            GitClient gitClient,
            AuditLogService auditLogService) {
        this.codeRepositoryRepository = codeRepositoryRepository;
        this.gitClient = gitClient;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CodeRepositoryEntity connectRepository(
            UUID tenantId,
            GitProviderType provider,
            String repoName,
            String repoUrl,
            String accessToken) {

        if (repoName == null || repoName.isBlank()) {
            throw new ValidationException("Repository name cannot be blank");
        }
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new ValidationException("Repository URL cannot be blank");
        }

        String normalizedRepo = repoName.trim().toLowerCase();

        if (codeRepositoryRepository.existsByTenantIdAndProviderAndRepoName(tenantId, provider, normalizedRepo)) {
            throw new ValidationException("Repository " + normalizedRepo + " is already connected for this tenant");
        }

        CodeRepositoryEntity repo = new CodeRepositoryEntity(
                tenantId,
                provider != null ? provider : GitProviderType.GITHUB,
                normalizedRepo,
                repoUrl.trim(),
                accessToken
        );

        CodeRepositoryEntity saved = codeRepositoryRepository.save(repo);

        auditLogService.recordCurrentContext(
                "GIT_REPOSITORY_CONNECTED",
                "repo:" + saved.getId(),
                null,
                String.format("provider:%s, repo:%s", saved.getProvider(), saved.getRepoName())
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<CodeRepositoryEntity> listConnectedRepositories(UUID tenantId) {
        return codeRepositoryRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public CodeRepositoryEntity getRepository(UUID tenantId, UUID repoId) {
        return codeRepositoryRepository.findByIdAndTenantId(repoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Connected repository not found: " + repoId));
    }

    @Transactional
    public void disconnectRepository(UUID tenantId, UUID repoId) {
        CodeRepositoryEntity repo = codeRepositoryRepository.findByIdAndTenantId(repoId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Connected repository not found: " + repoId));

        codeRepositoryRepository.delete(repo);

        auditLogService.recordCurrentContext(
                "GIT_REPOSITORY_DISCONNECTED",
                "repo:" + repoId,
                "repo:" + repo.getRepoName(),
                null
        );
    }

    @Transactional(readOnly = true)
    public boolean isRepositoryAllowlisted(UUID tenantId, String repoName) {
        if (repoName == null || repoName.isBlank()) {
            return false;
        }
        return codeRepositoryRepository.existsByTenantIdAndRepoName(tenantId, repoName.trim().toLowerCase());
    }

    /**
     * Retrieves code commit diff strictly validating that the repository is on the tenant's allowlist (PRD §26).
     */
    @Transactional(readOnly = true)
    public CommitDiffDto getCommitDiff(UUID tenantId, String repoName, String baseCommit, String headCommit) {
        if (!isRepositoryAllowlisted(tenantId, repoName)) {
            log.warn("Access denied: Repository '{}' is not on tenant {}'s allowlist", repoName, tenantId);
            throw new ForbiddenException(String.format(
                    "Repository '%s' is not authorized for tenant. Only explicitly allowlisted repositories may be queried (PRD §26).",
                    repoName));
        }

        CodeRepositoryEntity repo = codeRepositoryRepository.findByTenantIdAndRepoName(tenantId, repoName.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found: " + repoName));

        CommitDiffDto diff = gitClient.getCommitDiff(repo.getRepoName(), baseCommit, headCommit, repo.getAccessToken());

        auditLogService.recordCurrentContext(
                "GIT_DIFF_ACCESSED",
                "repo:" + repo.getRepoName(),
                null,
                String.format("base:%s, head:%s, files:%d", baseCommit, headCommit, diff.filesChanged().size())
        );

        return diff;
    }
}
