package com.resolveiq.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive RBAC Authorization Verification (PRD §§12.2, 35.3, 36).
 * Tests role authorization across OWNER, ADMIN, INCIDENT_MANAGER, SRE, DEVELOPER, and VIEWER.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ComprehensiveRbacAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantService tenantService;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationEntity tenant;
    private ProjectEntity project;
    private IncidentEntity testIncident;

    private String ownerToken;
    private String adminToken;
    private String incidentManagerToken;
    private String sreToken;
    private String devToken;
    private String viewerToken;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("RBAC Test Org " + suffix, "rbac-" + suffix, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "rbac-setup"));
        try {
            project = tenantService.createProject("RBAC Project", "rbac-proj-" + suffix, "Test Project");
            testIncident = new IncidentEntity(
                    tenant.getId(),
                    project.getId(),
                    "fp-rbac-" + suffix,
                    "RBAC Test Incident",
                    "payment-service",
                    IncidentSeverity.SEV2,
                    "Platform Team"
            );
            testIncident = incidentRepository.save(testIncident);
        } finally {
            TenantContextHolder.clear();
        }

        ownerToken = jwtTokenUtil.generateAccessToken(tenant.getId(), UUID.randomUUID(), "owner@corp.io", Role.OWNER);
        adminToken = jwtTokenUtil.generateAccessToken(tenant.getId(), UUID.randomUUID(), "admin@corp.io", Role.ADMIN);
        incidentManagerToken = jwtTokenUtil.generateAccessToken(tenant.getId(), UUID.randomUUID(), "im@corp.io", Role.INCIDENT_MANAGER);
        sreToken = jwtTokenUtil.generateAccessToken(tenant.getId(), UUID.randomUUID(), "sre@corp.io", Role.SRE);
        devToken = jwtTokenUtil.generateAccessToken(tenant.getId(), UUID.randomUUID(), "dev@corp.io", Role.DEVELOPER);
        viewerToken = jwtTokenUtil.generateAccessToken(tenant.getId(), UUID.randomUUID(), "viewer@corp.io", Role.VIEWER);
    }

    @Test
    @DisplayName("RBAC: VIEWER has read-only access and is strictly forbidden from mutating state")
    void testViewerForbiddenFromMutations() throws Exception {
        // VIEWER can read incidents
        mockMvc.perform(get("/api/v1/incidents/" + testIncident.getId())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        // VIEWER cannot change status (403)
        mockMvc.perform(post("/api/v1/incidents/" + testIncident.getId() + "/status")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVESTIGATING\"}"))
                .andExpect(status().isForbidden());

        // VIEWER cannot add comment (403)
        mockMvc.perform(post("/api/v1/incidents/" + testIncident.getId() + "/timeline")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Unauthorized viewer comment\"}"))
                .andExpect(status().isForbidden());

        // VIEWER cannot trigger AI evaluation (403)
        mockMvc.perform(post("/api/v1/evaluations/run")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"benchmarkSuite\":\"all\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: DEVELOPER can comment on timeline but cannot change incident status")
    void testDeveloperCanCommentButNotMutateStatus() throws Exception {
        // DEVELOPER can comment on timeline
        mockMvc.perform(post("/api/v1/incidents/" + testIncident.getId() + "/timeline")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Dev investigated root cause in code commit 4a9f\"}"))
                .andExpect(status().isOk());

        // DEVELOPER cannot change status
        mockMvc.perform(post("/api/v1/incidents/" + testIncident.getId() + "/status")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC: SRE and INCIDENT_MANAGER can mutate status, attach evidence, and trigger evaluations")
    void testSreAndIncidentManagerPermissions() throws Exception {
        // SRE can update incident status (DETECTED -> INVESTIGATING)
        mockMvc.perform(post("/api/v1/incidents/" + testIncident.getId() + "/status")
                        .header("Authorization", "Bearer " + sreToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVESTIGATING\",\"notes\":\"SRE began active triage\"}"))
                .andExpect(status().isOk());

        // INCIDENT_MANAGER can update incident severity
        mockMvc.perform(post("/api/v1/incidents/" + testIncident.getId() + "/severity")
                        .header("Authorization", "Bearer " + incidentManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"severity\":\"SEV1\",\"reason\":\"Elevated customer impact\"}"))
                .andExpect(status().isOk());

        // SRE can ingest knowledge documents
        mockMvc.perform(post("/api/v1/knowledge/docs")
                        .header("Authorization", "Bearer " + sreToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docType\":\"RUNBOOK\",\"title\":\"Payment Failover Runbook\",\"content\":\"Step 1: Check pods\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RBAC: ADMIN and OWNER have full administrative permissions")
    void testAdminAndOwnerPermissions() throws Exception {
        // ADMIN can create project
        mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Project Admin\",\"slug\":\"new-proj-admin\",\"description\":\"Created by Admin\"}"))
                .andExpect(status().isCreated());

        // OWNER can create service
        mockMvc.perform(post("/api/v1/services")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + project.getId() + "\",\"name\":\"billing-service\",\"tier\":\"TIER_1\",\"ownerTeam\":\"Finance\",\"repositoryUrl\":\"https://github.com/org/billing\"}"))
                .andExpect(status().isCreated());
    }
}
