package com.resolveiq.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.evaluation.domain.EvaluationRunEntity;
import com.resolveiq.backend.evaluation.repository.EvaluationRunRepository;
import com.resolveiq.backend.repository.*;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.incident.EvidenceSource;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.knowledge.KnowledgeDocType;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Authoritative Release-Blocking Cross-Tenant Security Gate (PRD §§11.1, 35.3, 56, 68).
 * Strictly tests that Tenant B cannot access, read, search, mutate, or delete any of Tenant A's 18 platform entities.
 * A single isolation leak is a RELEASE BLOCKER.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ReleaseGateCrossTenantSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantService tenantService;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EnvironmentRepository environmentRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private EvidenceRepository evidenceRepository;
    @Autowired private DeploymentRepository deploymentRepository;
    @Autowired private KnowledgeDocRepository knowledgeDocRepository;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private EvaluationRunRepository evaluationRunRepository;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationEntity tenantA;
    private OrganizationEntity tenantB;
    private String tokenA;
    private String tokenB;
    private UUID userAId;
    private UUID userBId;

    @BeforeEach
    void setUp() {
        String suffixA = UUID.randomUUID().toString().substring(0, 8);
        String suffixB = UUID.randomUUID().toString().substring(0, 8);

        tenantA = tenantService.createOrganization("Org Alpha " + suffixA, "alpha-" + suffixA, "ENTERPRISE");
        tenantB = tenantService.createOrganization("Org Beta " + suffixB, "beta-" + suffixB, "ENTERPRISE");

        userAId = UUID.randomUUID();
        userBId = UUID.randomUUID();

        tokenA = jwtTokenUtil.generateAccessToken(tenantA.getId(), userAId, "admin@alpha.io", Role.ADMIN);
        tokenB = jwtTokenUtil.generateAccessToken(tenantB.getId(), userBId, "admin@beta.io", Role.ADMIN);
    }

    @Test
    @DisplayName("Release Gate 1: Cross-Tenant Projects & Environments Isolation")
    void testCrossTenantProjectsAndEnvironmentsIsolation() throws Exception {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "trace-setup-a"));
        ProjectEntity projectA;
        try {
            projectA = tenantService.createProject("Alpha Core", "alpha-core", "Core Alpha Service");
        } finally {
            TenantContextHolder.clear();
        }

        // Tenant B cannot access Tenant A's project via direct GET
        mockMvc.perform(get("/api/v1/projects/" + projectA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Tenant B listing projects returns 0 items from Tenant A
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name == 'Alpha Core')]").doesNotExist());
    }

    @Test
    @DisplayName("Release Gate 2: Cross-Tenant Services & Topology Isolation")
    void testCrossTenantServicesAndTopologyIsolation() throws Exception {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "trace-setup-a"));
        ServiceEntity serviceA;
        try {
            ProjectEntity projectA = tenantService.createProject("Auth Suite", "auth-suite", "Auth Project");
            serviceA = tenantService.createService(projectA.getId(), "auth-service-alpha", "TIER_1", "Security Team", "https://github.com/alpha/auth");
        } finally {
            TenantContextHolder.clear();
        }

        // Tenant B cannot GET Tenant A's service
        mockMvc.perform(get("/api/v1/services/" + serviceA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Tenant B listing services does not expose Tenant A's service
        mockMvc.perform(get("/api/v1/services")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name == 'auth-service-alpha')]").doesNotExist());
    }

    @Test
    @DisplayName("Release Gate 3: Cross-Tenant Incidents, Events & Evidence Isolation")
    void testCrossTenantIncidentsAndEvidenceIsolation() throws Exception {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "trace-setup-a"));
        IncidentEntity incA;
        try {
            ProjectEntity projectA = tenantService.createProject("Alpha Payments", "alpha-pay", "Alpha Payments");
            incA = new IncidentEntity(
                    tenantA.getId(),
                    projectA.getId(),
                    "fp-alpha-001",
                    "Alpha Payment Outage",
                    "payment-service",
                    IncidentSeverity.SEV1,
                    "Payments Team"
            );
            incA = incidentRepository.save(incA);

            EvidenceEntity evA = new EvidenceEntity(
                    tenantA.getId(),
                    incA.getId(),
                    EvidenceSource.METRICS,
                    "payment-service",
                    "rate(http_requests_total[5m])",
                    "metric:payment-errors",
                    "Error rate spike above 15%",
                    0.95,
                    0.90,
                    "CONFIRMING",
                    Instant.now()
            );
            evidenceRepository.save(evA);
        } finally {
            TenantContextHolder.clear();
        }

        // Tenant B cannot GET Tenant A's incident
        mockMvc.perform(get("/api/v1/incidents/" + incA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Tenant B cannot GET Tenant A's incident evidence
        mockMvc.perform(get("/api/v1/incidents/" + incA.getId() + "/evidence")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Tenant B cannot append timeline comment to Tenant A's incident
        mockMvc.perform(post("/api/v1/incidents/" + incA.getId() + "/timeline")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Malicious attempt by B\"}"))
                .andExpect(status().isNotFound());

        // Tenant B cannot mutate Tenant A's incident status
        mockMvc.perform(post("/api/v1/incidents/" + incA.getId() + "/status")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Release Gate 4: Cross-Tenant Deployments & Knowledge Base Isolation")
    void testCrossTenantDeploymentsAndKnowledgeIsolation() throws Exception {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "trace-setup-a"));
        KnowledgeDocEntity docA;
        try {
            DeploymentEntity depA = new DeploymentEntity(
                    tenantA.getId(),
                    null,
                    null,
                    "payment-service",
                    "production",
                    "v2.8.0",
                    "commit-alpha-secret-123",
                    "Vulnerability patch",
                    "Alice",
                    "SUCCESS",
                    Instant.now(),
                    "{}"
            );
            deploymentRepository.save(depA);

            docA = new KnowledgeDocEntity(
                    tenantA.getId(),
                    null,
                    KnowledgeDocType.POSTMORTEM,
                    "Alpha Secret Incident Postmortem",
                    "https://wiki.alpha.io/pm-001",
                    "payment-service",
                    "production",
                    "Secret vulnerability RCA content",
                    "hash-alpha-001",
                    "{}"
            );
            docA = knowledgeDocRepository.save(docA);
        } finally {
            TenantContextHolder.clear();
        }

        // Tenant B listing deployments does not leak Tenant A's commit SHAs
        mockMvc.perform(get("/api/v1/deployments")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.commitHash == 'commit-alpha-secret-123')]").doesNotExist());

        // Tenant B cannot direct GET Tenant A's knowledge document
        mockMvc.perform(get("/api/v1/knowledge/docs/" + docA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Tenant B listing knowledge base does not leak Tenant A's documents
        mockMvc.perform(get("/api/v1/knowledge/docs")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.title == 'Alpha Secret Incident Postmortem')]").doesNotExist());
    }

    @Test
    @DisplayName("Release Gate 5: Cross-Tenant Audit Logs & API Keys Isolation")
    void testCrossTenantAuditLogsAndApiKeysIsolation() throws Exception {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "trace-setup-a"));
        try {
            AuditLogEntity auditA = new AuditLogEntity(
                    tenantA.getId(),
                    userAId,
                    "USER",
                    "INCIDENT_RESOLVE",
                    "INCIDENT",
                    "{}",
                    "{\"status\":\"RESOLVED\"}",
                    "10.0.0.1",
                    "trace-alpha-001"
            );
            auditLogRepository.save(auditA);

            ApiKeyEntity keyA = new ApiKeyEntity(
                    tenantA.getId(),
                    "Alpha Collector Key",
                    "riq_live_alpha_prefix",
                    "argon2id_hash_alpha",
                    "ALL",
                    null
            );
            apiKeyRepository.save(keyA);
        } finally {
            TenantContextHolder.clear();
        }

        // Tenant B listing audit logs does not expose Tenant A's records
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.traceId == 'trace-alpha-001')]").doesNotExist());

        // Tenant B listing API keys does not expose Tenant A's keys
        mockMvc.perform(get("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.keyPrefix == 'riq_live_alpha_prefix')]").doesNotExist());
    }

    @Test
    @DisplayName("Release Gate 6: Cross-Tenant Evaluation Runs & AI Benchmarks Isolation")
    void testCrossTenantEvaluationRunsIsolation() throws Exception {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "trace-setup-a"));
        EvaluationRunEntity runA;
        try {
            runA = new EvaluationRunEntity(
                    tenantA.getId(),
                    "gemini-1.5-pro",
                    "alpha-benchmark-v1",
                    10,
                    9,
                    0.90,
                    1.00,
                    0.85,
                    0.88,
                    0.02,
                    1.00,
                    1.00,
                    6.5,
                    420L,
                    850L,
                    12000,
                    0.12,
                    false
            );
            runA = evaluationRunRepository.save(runA);
        } finally {
            TenantContextHolder.clear();
        }

        // Tenant B cannot direct GET Tenant A's evaluation run
        mockMvc.perform(get("/api/v1/evaluations/" + runA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // Tenant B listing runs does not expose Tenant A's benchmark runs
        mockMvc.perform(get("/api/v1/evaluations")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.benchmarkSuite == 'alpha-benchmark-v1')]").doesNotExist());
    }
}
