package com.resolveiq.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.AuditLogEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.backend.service.TenantService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RbacAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.resolveiq.backend.repository.IncidentRepository incidentRepository;

    private OrganizationEntity tenant;
    private UUID incidentId;

    @BeforeEach
    void setUp() {
        String slug = "rbac-org-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("RBAC Test Corp", slug, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-setup"));
        try {
            com.resolveiq.backend.domain.ProjectEntity project = tenantService.createProject("Core Project", "core-" + slug, "Test project");
            com.resolveiq.backend.domain.IncidentEntity incident = new com.resolveiq.backend.domain.IncidentEntity(
                    tenant.getId(),
                    project.getId(),
                    "fingerprint-" + UUID.randomUUID(),
                    "Test Outage",
                    "payment-service",
                    com.resolveiq.common.incident.IncidentSeverity.SEV2,
                    "Checkout Team"
            );
            incident.setStatus(com.resolveiq.common.incident.IncidentStatus.DETECTED);
            incidentId = incidentRepository.save(incident).getId();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Acceptance Criterion 3: User with VIEWER role attempting mutation is rejected with 403 and attempt is logged in audit_logs")
    void testViewerRoleDeniedAndLogged() throws Exception {
        UUID viewerId = UUID.randomUUID();
        String viewerToken = jwtTokenUtil.generateAccessToken(tenant.getId(), viewerId, "viewer.bob@test.com", Role.VIEWER);

        Map<String, String> payload = Map.of("status", "MITIGATING");

        // Attempt state-changing mutation as VIEWER
        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.traceId").exists());

        // Verify that the unauthorized attempt was recorded in the immutable audit log per PRD Section 12.3
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "trace-audit-check"));
        try {
            List<AuditLogEntity> auditLogs = auditLogService.getAuditLogsByAction("UNAUTHORIZED_ACCESS_ATTEMPT");
            assertThat(auditLogs).isNotEmpty();
            AuditLogEntity logEntry = auditLogs.get(0);
            assertThat(logEntry.getTenantId()).isEqualTo(tenant.getId());
            assertThat(logEntry.getActorId()).isEqualTo(viewerId);
            assertThat(logEntry.getTargetResource()).contains("/api/v1/incidents/" + incidentId + "/status");
            assertThat(logEntry.getAfterState()).contains("Denied role VIEWER");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Authorized user (SRE) successfully performs state-changing mutation")
    void testSreRoleAllowed() throws Exception {
        UUID sreId = UUID.randomUUID();
        String sreToken = jwtTokenUtil.generateAccessToken(tenant.getId(), sreId, "sre.priya@test.com", Role.SRE);

        Map<String, String> payload = Map.of("status", "INVESTIGATING");

        mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + sreToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));
    }
}
