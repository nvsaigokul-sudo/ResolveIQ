package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.CodeRepositoryEntity;
import com.resolveiq.backend.integrations.git.CommitDiffDto;
import com.resolveiq.backend.integrations.git.GitIntegrationService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ForbiddenException;
import com.resolveiq.common.integration.GitProviderType;
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
 * REST controller for Code-Aware Git Integrations (PRD §26, §35).
 */
@RestController
@RequestMapping({"/api/v1/integrations/git", "/api/v1/integrations/git/repositories"})
public class GitIntegrationController {

    private final GitIntegrationService gitIntegrationService;

    public GitIntegrationController(GitIntegrationService gitIntegrationService) {
        this.gitIntegrationService = gitIntegrationService;
    }

    public record ConnectRepositoryRequest(
            @NotNull(message = "Git provider cannot be null") GitProviderType provider,
            @NotBlank(message = "Repository name cannot be blank") String repoName,
            @NotBlank(message = "Repository URL cannot be blank") String repoUrl,
            String accessToken
    ) {}

    @PostMapping
    public ResponseEntity<ApiResponse<CodeRepositoryEntity>> connectRepository(
            @Valid @RequestBody ConnectRepositoryRequest request) {
        requirePermission("connect git repository");
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        CodeRepositoryEntity repo = gitIntegrationService.connectRepository(
                tenantId,
                request.provider(),
                request.repoName(),
                request.repoUrl(),
                request.accessToken()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(repo));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CodeRepositoryEntity>>> listRepositories() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<CodeRepositoryEntity> repos = gitIntegrationService.listConnectedRepositories(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(repos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CodeRepositoryEntity>> getRepository(@PathVariable("id") UUID repoId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        CodeRepositoryEntity repo = gitIntegrationService.getRepository(tenantId, repoId);
        return ResponseEntity.ok(ApiResponse.ok(repo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disconnectRepository(@PathVariable("id") UUID repoId) {
        requirePermission("disconnect git repository");
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        gitIntegrationService.disconnectRepository(tenantId, repoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/diff")
    public ResponseEntity<ApiResponse<CommitDiffDto>> getCommitDiff(
            @RequestParam("repoName") String repoName,
            @RequestParam(value = "baseCommit", required = false, defaultValue = "v2.7") String baseCommit,
            @RequestParam(value = "headCommit", required = false, defaultValue = "d7a4b81") String headCommit) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        CommitDiffDto diff = gitIntegrationService.getCommitDiff(tenantId, repoName, baseCommit, headCommit);
        return ResponseEntity.ok(ApiResponse.ok(diff));
    }

    private void requirePermission(String action) {
        Role role = TenantContextHolder.getContext().map(TenantContext::role).orElse(Role.VIEWER);
        if (!role.canManageIntegrations()) {
            throw new ForbiddenException("Role " + role + " is not authorized to " + action);
        }
    }
}
