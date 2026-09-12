package com.resolveiq.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.EvidenceEntity;
import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.repository.EvidenceRepository;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.incident.EvidenceSource;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentSecurityAndCrossTenantTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private OrganizationEntity tenantA;
    private OrganizationEntity tenantB;
    private IncidentEntity incidentA;
    private IncidentEntity incidentB;
    private EvidenceEntity evidenceA;
    private EvidenceEntity evidenceB;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenantA = tenantService.createOrganization("Tenant Alpha Inc", "alpha-" + suffix, "ENTERPRISE");
        tenantB = tenantService.createOrganization("Tenant Beta Corp", "beta-" + suffix, "ENTERPRISE");

        // Set up Tenant A
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "trace-setup-a"));
        try {
            ProjectEntity projectA = tenantService.createProject("Payments Alpha", "pay-" + suffix, "Tenant A");
            incidentA = new IncidentEntity(
                    tenantA.getId(),
                    projectA.getId(),
                    "fp-a-" + suffix,
                    "Payment failure Alpha",
                    "payment-service",
                    IncidentSeverity.SEV1,
                    "Alpha SRE"
            );
            incidentA.setStatus(IncidentStatus.INVESTIGATING);
            incidentA = incidentRepository.save(incidentA);

            evidenceA = new EvidenceEntity(
                    tenantA.getId(),
                    incidentA.getId(),
                    EvidenceSource.LOGS,
                    "payment-service",
                    "grep Error",
                    "ref-a",
                    "<telemetry_evidence>Alpha secret error</telemetry_evidence>",
                    1.0, 0.9, null, Instant.now()
            );
            evidenceA = evidenceRepository.save(evidenceA);
        } finally {
            TenantContextHolder.clear();
        }

        // Set up Tenant B
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantB.getId(), "trace-setup-b"));
        try {
            ProjectEntity projectB = tenantService.createProject("Logistics Beta", "log-" + suffix, "Tenant B");
            incidentB = new IncidentEntity(
                    tenantB.getId(),
                    projectB.getId(),
                    "fp-b-" + suffix,
                    "Shipping outage Beta",
                    "shipping-service",
                    IncidentSeverity.SEV2,
                    "Beta SRE"
            );
            incidentB.setStatus(IncidentStatus.INVESTIGATING);
            incidentB = incidentRepository.save(incidentB);

            evidenceB = new EvidenceEntity(
                    tenantB.getId(),
                    incidentB.getId(),
                    EvidenceSource.LOGS,
                    "shipping-service",
                    "grep Error",
                    "ref-b",
                    "<telemetry_evidence>Beta confidential log</telemetry_evidence>",
                    1.0, 0.9, null, Instant.now()
            );
            evidenceB = evidenceRepository.save(evidenceB);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("0% cross-tenant data leakage: Tenant A user cannot list or view Tenant B incidents (PRD §11.3)")
    void testZeroCrossTenantIncidentLeakage() throws Exception {
        UUID userA = UUID.randomUUID();
        String tokenA = jwtTokenUtil.generateAccessToken(tenantA.getId(), userA, "user@alpha.com", Role.SRE);

        // 1. List incidents as Tenant A: must only contain Tenant A incident, never Tenant B
        mockMvc.perform(get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[?(@.id == '" + incidentA.getId() + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.id == '" + incidentB.getId() + "')]").doesNotExist());

        // 2. Direct attempt to fetch Tenant B's incident by ID: returns 404 (not 403) per PRD §35.3
        mockMvc.perform(get("/api/v1/incidents/" + incidentB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        // 3. Direct attempt to fetch Tenant B's timeline: returns 404
        mockMvc.perform(get("/api/v1/incidents/" + incidentB.getId() + "/timeline")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // 4. Direct attempt to fetch Tenant B's evidence: returns 404
        mockMvc.perform(get("/api/v1/incidents/" + incidentB.getId() + "/evidence")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cross-tenant mutation attack: Tenant A cannot mutate Tenant B incident (PRD §11.1)")
    void testCrossTenantMutationBlocked() throws Exception {
        UUID userA = UUID.randomUUID();
        String tokenA = jwtTokenUtil.generateAccessToken(tenantA.getId(), userA, "user@alpha.com", Role.ADMIN);

        Map<String, String> payload = Map.of("status", "RESOLVED", "notes", "Malicious cross-tenant close");

        mockMvc.perform(post("/api/v1/incidents/" + incidentB.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RBAC matrix for Incident endpoints: Viewer denied mutations, Developer limited, SRE allowed (PRD §12.2)")
    void testIncidentRbacMatrix() throws Exception {
        UUID viewerId = UUID.randomUUID();
        String viewerToken = jwtTokenUtil.generateAccessToken(tenantA.getId(), viewerId, "viewer@alpha.com", Role.VIEWER);

        UUID devId = UUID.randomUUID();
        String devToken = jwtTokenUtil.generateAccessToken(tenantA.getId(), devId, "dev@alpha.com", Role.DEVELOPER);

        UUID sreId = UUID.randomUUID();
        String sreToken = jwtTokenUtil.generateAccessToken(tenantA.getId(), sreId, "sre@alpha.com", Role.SRE);

        // 1. Viewer cannot change severity -> 403 Forbidden
        mockMvc.perform(post("/api/v1/incidents/" + incidentA.getId() + "/severity")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("severity", "SEV4", "reason", "Downgrade"))))
                .andExpect(status().isForbidden());

        // 2. Developer cannot change severity -> 403 Forbidden
        mockMvc.perform(post("/api/v1/incidents/" + incidentA.getId() + "/severity")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("severity", "SEV4", "reason", "Downgrade"))))
                .andExpect(status().isForbidden());

        // 3. Developer can add comments -> 200 OK
        mockMvc.perform(post("/api/v1/incidents/" + incidentA.getId() + "/timeline")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("comment", "Investigating service logs."))))
                .andExpect(status().isOk());

        // 4. SRE can change severity -> 200 OK
        mockMvc.perform(post("/api/v1/incidents/" + incidentA.getId() + "/severity")
                        .header("Authorization", "Bearer " + sreToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("severity", "SEV2", "reason", "Impact verified"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("SEV2"));
    }
}
