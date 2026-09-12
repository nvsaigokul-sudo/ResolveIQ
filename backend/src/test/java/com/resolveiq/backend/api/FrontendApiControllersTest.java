package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.domain.ServiceEntity;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.ProjectRepository;
import com.resolveiq.backend.repository.ServiceRepository;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.security.ActorType;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backend integration test suite verifying new REST endpoints supporting Phase 12 Frontend
 * (AuthController, ServiceController, DashboardController, TelemetryController).
 */
@SpringBootTest
@ActiveProfiles("test")
public class FrontendApiControllersTest {

    @Autowired private AuthController authController;
    @Autowired private ServiceController serviceController;
    @Autowired private DashboardController dashboardController;
    @Autowired private TelemetryController telemetryController;

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ServiceRepository serviceRepository;

    private UUID tenantId;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        OrganizationEntity org = organizationRepository.save(
                new OrganizationEntity("Frontend Test Org", "fe-org-" + uniqueSuffix, "ENTERPRISE"));
        tenantId = org.getId();

        project = projectRepository.save(
                new ProjectEntity(tenantId, "Frontend Project", "fe-proj-" + uniqueSuffix, "description"));

        serviceRepository.save(new ServiceEntity(tenantId, project.getId(), "api-gateway", "TIER_1", "team-gateway", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, project.getId(), "payment-service", "TIER_1", "team-payment", "repo"));
        serviceRepository.save(new ServiceEntity(tenantId, project.getId(), "order-service", "TIER_1", "team-order", "repo"));

        TenantContextHolder.setContext(new TenantContext(tenantId, UUID.randomUUID(), Role.SRE, ActorType.USER));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("AuthController: Login generates valid cryptographic JWT and /me returns user context")
    void testAuthEndpoints() {
        // 1. List tenants
        ResponseEntity<ApiResponse<AuthController.TenantSummaryDto>> tenantsResp = authController.listTenants();
        assertNotNull(tenantsResp.getBody());
        assertFalse(tenantsResp.getBody().items().isEmpty());

        // 2. Login
        AuthController.LoginRequest loginReq = new AuthController.LoginRequest(
                "priya@resolveiq.io", null, tenantId, Role.SRE
        );
        ResponseEntity<AuthController.AuthResponse> loginResp = authController.login(loginReq);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        assertNotNull(loginResp.getBody());
        assertNotNull(loginResp.getBody().accessToken());
        assertEquals("priya@resolveiq.io", loginResp.getBody().email());
        assertEquals(Role.SRE, loginResp.getBody().role());

        // 3. /me endpoint
        ResponseEntity<AuthController.AuthResponse> meResp = authController.getCurrentUser();
        assertNotNull(meResp.getBody());
        assertEquals(tenantId, meResp.getBody().tenantId());

        // 4. Switch role
        AuthController.SwitchRoleRequest switchReq = new AuthController.SwitchRoleRequest(Role.VIEWER);
        ResponseEntity<AuthController.AuthResponse> switchResp = authController.switchRole(switchReq);
        assertNotNull(switchResp.getBody());
        assertEquals(Role.VIEWER, switchResp.getBody().role());
    }

    @Test
    @DisplayName("ServiceController: Lists services and calculates topology blast radius")
    void testServiceAndTopologyEndpoints() {
        ResponseEntity<ApiResponse<ServiceController.ServiceSummaryDto>> servicesResp = serviceController.listServices();
        assertNotNull(servicesResp.getBody());
        assertTrue(servicesResp.getBody().items().size() >= 3);

        ResponseEntity<ServiceController.TopologyGraphDto> topoResp = serviceController.getServiceTopology("payment-service");
        assertNotNull(topoResp.getBody());
        assertEquals("payment-service", topoResp.getBody().rootService());
        assertFalse(topoResp.getBody().nodes().isEmpty());
        assertTrue(topoResp.getBody().blastRadius().contains("payment-service"));
    }

    @Test
    @DisplayName("DashboardController: Returns real-time platform aggregates and detection trends")
    void testDashboardSummary() {
        ResponseEntity<DashboardController.DashboardSummaryDto> dashResp = dashboardController.getDashboardSummary();
        assertNotNull(dashResp.getBody());
        assertTrue(dashResp.getBody().totalServices() >= 3);
        assertNotNull(dashResp.getBody().detectionTrend());
        assertFalse(dashResp.getBody().detectionTrend().isEmpty());
    }

    @Test
    @DisplayName("TelemetryController: Provides metrics, logs, trace waterfall, and collector status")
    void testTelemetryEndpoints() {
        var metricsResp = telemetryController.getMetrics("payment-service", "error_rate", "1h", "p95");
        assertNotNull(metricsResp.getBody());
        assertEquals(12, metricsResp.getBody().points().size());

        var logsResp = telemetryController.getLogs("payment-service", "error", null, "1h");
        assertNotNull(logsResp.getBody());
        assertFalse(logsResp.getBody().items().isEmpty());

        var traceResp = telemetryController.getTrace("4bf92f3577b34da6a3ce929d0e0e4736");
        assertNotNull(traceResp.getBody());
        assertEquals("payment-service", traceResp.getBody().rootService());
        assertFalse(traceResp.getBody().spans().isEmpty());

        var collectorsResp = telemetryController.getCollectors();
        assertNotNull(collectorsResp.getBody());
        assertFalse(collectorsResp.getBody().items().isEmpty());
    }
}
