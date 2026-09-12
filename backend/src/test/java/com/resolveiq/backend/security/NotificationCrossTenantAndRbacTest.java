package com.resolveiq.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.CodeRepositoryEntity;
import com.resolveiq.backend.domain.NotificationChannelEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.repository.CodeRepositoryRepository;
import com.resolveiq.backend.repository.NotificationChannelRepository;
import com.resolveiq.backend.repository.NotificationRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.integration.GitProviderType;
import com.resolveiq.common.notification.NotificationChannelType;
import com.resolveiq.common.security.JwtTokenUtil;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationCrossTenantAndRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private NotificationChannelRepository channelRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CodeRepositoryRepository codeRepositoryRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private OrganizationEntity tenantA;
    private OrganizationEntity tenantB;
    private NotificationChannelEntity channelA;
    private CodeRepositoryEntity repoA;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        channelRepository.deleteAll();
        codeRepositoryRepository.deleteAll();

        String runId = UUID.randomUUID().toString().substring(0, 8);
        tenantA = tenantService.createOrganization("TenantA-" + runId, "tenant-a-" + runId, "ENTERPRISE");
        tenantB = tenantService.createOrganization("TenantB-" + runId, "tenant-b-" + runId, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "setup-tenant-a"));
        try {
            channelA = channelRepository.save(new NotificationChannelEntity(
                    tenantA.getId(), "Tenant A Ops Slack", NotificationChannelType.SLACK,
                    "https://hooks.slack.com/mock/A", null, IncidentSeverity.SEV3
            ));
            repoA = codeRepositoryRepository.save(new CodeRepositoryEntity(
                    tenantA.getId(), GitProviderType.GITHUB, "service-alpha",
                    "https://github.com/org-a/service-alpha.git", "tokenA"
            ));
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Cross-tenant isolation: Tenant B cannot access Tenant A's notification channels (returns 404)")
    void testCrossTenantChannelAccessBlocked() throws Exception {
        String tokenB = jwtTokenUtil.generateAccessToken(tenantB.getId(), UUID.randomUUID(), "admin.bob@tenantb.com", Role.ADMIN);

        // GET Tenant A's channel
        mockMvc.perform(get("/api/v1/notifications/channels/" + channelA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        // DELETE Tenant A's channel
        mockMvc.perform(delete("/api/v1/notifications/channels/" + channelA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        // LIST channels returns only Tenant B's channels
        mockMvc.perform(get("/api/v1/notifications/channels")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("Cross-tenant isolation: Tenant B cannot access or delete Tenant A's code repositories (returns 404)")
    void testCrossTenantCodeRepositoryAccessBlocked() throws Exception {
        String tokenB = jwtTokenUtil.generateAccessToken(tenantB.getId(), UUID.randomUUID(), "admin.bob@tenantb.com", Role.ADMIN);

        // GET Tenant A's repo
        mockMvc.perform(get("/api/v1/integrations/git/repositories/" + repoA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        // DELETE Tenant A's repo
        mockMvc.perform(delete("/api/v1/integrations/git/repositories/" + repoA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        // LIST repositories returns empty list for Tenant B
        mockMvc.perform(get("/api/v1/integrations/git/repositories")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("RBAC Authorization: VIEWER role cannot create or delete notification channels (returns 403)")
    void testViewerRoleDeniedNotificationChannelMutation() throws Exception {
        String viewerToken = jwtTokenUtil.generateAccessToken(tenantA.getId(), UUID.randomUUID(), "viewer@tenanta.com", Role.VIEWER);

        Map<String, Object> channelPayload = Map.of(
                "name", "Unauthorized Channel",
                "channelType", "SLACK",
                "destination", "https://hooks.slack.com/mock/unauth",
                "minSeverity", "SEV2"
        );

        mockMvc.perform(post("/api/v1/notifications/channels")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(channelPayload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/notifications/channels/" + channelA.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC Authorization: VIEWER role cannot connect or disconnect code repositories (returns 403)")
    void testViewerRoleDeniedGitMutation() throws Exception {
        String viewerToken = jwtTokenUtil.generateAccessToken(tenantA.getId(), UUID.randomUUID(), "viewer@tenanta.com", Role.VIEWER);

        Map<String, Object> repoPayload = Map.of(
                "provider", "GITHUB",
                "repoName", "unauthorized-repo",
                "repoUrl", "https://github.com/org-a/unauthorized-repo.git",
                "accessToken", "token"
        );

        mockMvc.perform(post("/api/v1/integrations/git/repositories")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(repoPayload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/integrations/git/repositories/" + repoA.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
