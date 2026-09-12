package com.resolveiq.backend.integrations;

import com.resolveiq.backend.domain.CodeRepositoryEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.integrations.git.CommitDiffDto;
import com.resolveiq.backend.integrations.git.GitIntegrationService;
import com.resolveiq.backend.investigation.tools.GetCodeChangesTool;
import com.resolveiq.backend.investigation.tools.ToolExecutionResult;
import com.resolveiq.backend.repository.CodeRepositoryRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.exception.ForbiddenException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.integration.GitProviderType;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GitIntegrationAndAllowlistTest {

    @Autowired
    private GitIntegrationService gitIntegrationService;

    @Autowired
    private CodeRepositoryRepository codeRepositoryRepository;

    @Autowired
    private GetCodeChangesTool getCodeChangesTool;

    @Autowired
    private TenantService tenantService;

    private OrganizationEntity testTenant;
    private ProjectEntity testProject;

    @BeforeEach
    void setUp() {
        codeRepositoryRepository.deleteAll();

        String runId = UUID.randomUUID().toString().substring(0, 8);
        testTenant = tenantService.createOrganization("GitTestOrg-" + runId, "git-test-" + runId, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(testTenant.getId(), "test-setup"));
        testProject = tenantService.createProject("Project-" + runId, "proj-" + runId, "Test Project");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Connect repository adds repository to tenant allowlist")
    void testConnectRepositoryAllowlist() {
        CodeRepositoryEntity repo = gitIntegrationService.connectRepository(
                testTenant.getId(),
                GitProviderType.GITHUB,
                "order-service",
                "https://github.com/acme/order-service.git",
                "ghp_testToken12345"
        );

        assertThat(repo).isNotNull();
        assertThat(repo.getId()).isNotNull();
        assertThat(repo.getRepoName()).isEqualTo("order-service");

        // Allowlist query returns true
        assertThat(gitIntegrationService.isRepositoryAllowlisted(testTenant.getId(), "order-service")).isTrue();
        assertThat(gitIntegrationService.isRepositoryAllowlisted(testTenant.getId(), "ORDER-SERVICE")).isTrue(); // Case insensitive
        assertThat(gitIntegrationService.isRepositoryAllowlisted(testTenant.getId(), "unapproved-service")).isFalse();
    }

    @Test
    @DisplayName("Connecting duplicate repository throws ValidationException")
    void testDuplicateRepositoryRejected() {
        gitIntegrationService.connectRepository(
                testTenant.getId(),
                GitProviderType.GITHUB,
                "billing-service",
                "https://github.com/acme/billing-service.git",
                "token"
        );

        assertThatThrownBy(() -> gitIntegrationService.connectRepository(
                testTenant.getId(),
                GitProviderType.GITHUB,
                "billing-service",
                "https://github.com/acme/billing-service.git",
                "token"
        )).isInstanceOf(ValidationException.class)
                .hasMessageContaining("already connected");
    }

    @Test
    @DisplayName("Structural allowlist enforcement: getCommitDiff throws ForbiddenException for un-allowlisted repo")
    void testStructuralAllowlistEnforcement() {
        // Connect allowed repo
        gitIntegrationService.connectRepository(
                testTenant.getId(),
                GitProviderType.GITHUB,
                "payment-service",
                "https://github.com/acme/payment-service.git",
                "token"
        );

        // Allowed repository succeeds
        CommitDiffDto diff = gitIntegrationService.getCommitDiff(
                testTenant.getId(), "payment-service", "v1.0", "v1.1"
        );
        assertThat(diff).isNotNull();
        assertThat(diff.diffPatch()).contains("maximum-pool-size");

        // Un-allowlisted repository strictly throws ForbiddenException
        assertThatThrownBy(() -> gitIntegrationService.getCommitDiff(
                testTenant.getId(), "secret-internal-service", "v1.0", "v1.1"
        )).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("not authorized for tenant")
                .hasMessageContaining("PRD §26");
    }

    @Test
    @DisplayName("GetCodeChangesTool checks allowlist when repositories are configured")
    void testGetCodeChangesToolAllowlistEnforcement() {
        // Connect allowed repo
        gitIntegrationService.connectRepository(
                testTenant.getId(),
                GitProviderType.GITHUB,
                "inventory-service",
                "https://github.com/acme/inventory-service.git",
                "token"
        );

        UUID incidentId = UUID.randomUUID();

        // 1. Tool execution for allowed service succeeds
        ToolExecutionResult allowedResult = getCodeChangesTool.execute(
                testTenant.getId(), incidentId, Map.of("service", "inventory-service")
        );
        assertThat(allowedResult.success()).isTrue();
        assertThat(allowedResult.formattedResult()).contains("telemetry_data source=\"code_changes\"");
        assertThat(allowedResult.formattedResult()).contains("inventory-service");

        // 2. Tool execution for unauthorized service fails with clear PRD §26 violation message
        ToolExecutionResult deniedResult = getCodeChangesTool.execute(
                testTenant.getId(), incidentId, Map.of("service", "unauthorized-payroll-service")
        );
        assertThat(deniedResult.success()).isFalse();
        assertThat(deniedResult.formattedResult()).contains("not on tenant authorized allowlist (PRD §26)");
    }
}
