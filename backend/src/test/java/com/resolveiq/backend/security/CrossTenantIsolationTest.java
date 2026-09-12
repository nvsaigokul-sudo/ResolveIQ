package com.resolveiq.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.domain.ServiceEntity;
import com.resolveiq.backend.repository.ProjectRepository;
import com.resolveiq.backend.repository.ServiceRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CrossTenantIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private ObjectMapper objectMapper;

    private OrganizationEntity tenantA;
    private OrganizationEntity tenantB;
    private ProjectEntity projectA;
    private ProjectEntity projectB;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        tenantA = tenantService.createOrganization("Tenant Alpha", "alpha-" + uniqueSuffix, "ENTERPRISE");
        tenantB = tenantService.createOrganization("Tenant Beta", "beta-" + uniqueSuffix, "ENTERPRISE");

        // Create project in Tenant A
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "trace-a"));
        try {
            projectA = tenantService.createProject("Payment Gateway", "payment-gw-" + uniqueSuffix, "Tenant A project");
            tenantService.createService(projectA.getId(), "payment-service", "TIER_1", "Checkout Team", "https://git/pay");
        } finally {
            TenantContextHolder.clear();
        }

        // Create project in Tenant B
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantB.getId(), "trace-b"));
        try {
            projectB = tenantService.createProject("Logistics Fleet", "logistics-" + uniqueSuffix, "Tenant B project");
            tenantService.createService(projectB.getId(), "shipping-service", "TIER_1", "Logistics Team", "https://git/ship");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Acceptance Criterion 1: 0% cross-tenant data leakage at repository and service layer")
    void testZeroCrossTenantDataLeakageInRepository() {
        // Authenticate as Tenant A
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "trace-query-a"));
        try {
            List<ProjectEntity> projects = projectRepository.findAllForCurrentTenant();
            assertThat(projects).isNotEmpty();
            assertThat(projects).allMatch(p -> p.getTenantId().equals(tenantA.getId()));
            assertThat(projects).noneMatch(p -> p.getTenantId().equals(tenantB.getId()));
            assertThat(projects).noneMatch(p -> p.getId().equals(projectB.getId()));

            List<ServiceEntity> services = serviceRepository.findAllForCurrentTenant();
            assertThat(services).isNotEmpty();
            assertThat(services).allMatch(s -> s.getTenantId().equals(tenantA.getId()));
            assertThat(services).noneMatch(s -> s.getTenantId().equals(tenantB.getId()));

            // Direct attempt to query Tenant B's project by ID under Tenant A context must return empty
            assertThat(projectRepository.findByIdAndTenantId(projectB.getId(), tenantA.getId())).isEmpty();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Acceptance Criterion 2: Client cannot override tenant context via X-Tenant-Id header")
    void testHeaderTamperingImmunity() throws Exception {
        UUID userAId = UUID.randomUUID();
        String tokenTenantA = jwtTokenUtil.generateAccessToken(tenantA.getId(), userAId, "sre.priya@alpha.com", Role.SRE);

        // Client presents Tenant A JWT token but maliciously attaches X-Tenant-Id header for Tenant B
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + tokenTenantA)
                        .header("X-Tenant-Id", tenantB.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                // Assert that only Tenant A's project is returned and Tenant B's project is NOT present
                .andExpect(jsonPath("$.items[?(@.id == '" + projectA.getId() + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.id == '" + projectB.getId() + "')]").doesNotExist());
    }
}
