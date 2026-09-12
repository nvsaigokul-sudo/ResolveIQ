package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.DeploymentEntity;
import com.resolveiq.backend.domain.ServiceEntity;
import com.resolveiq.backend.repository.DeploymentRepository;
import com.resolveiq.backend.repository.ServiceRepository;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ForbiddenException;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for registering and querying service deployments (PRD §25.5, §36.1).
 */
@RestController
@RequestMapping("/api/v1/deployments")
public class DeploymentController {

    private final DeploymentRepository deploymentRepository;
    private final ServiceRepository serviceRepository;
    private final AuditLogService auditLogService;

    public DeploymentController(
            DeploymentRepository deploymentRepository,
            ServiceRepository serviceRepository,
            AuditLogService auditLogService) {
        this.deploymentRepository = deploymentRepository;
        this.serviceRepository = serviceRepository;
        this.auditLogService = auditLogService;
    }

    public record CreateDeploymentRequest(
            @NotBlank(message = "serviceName cannot be blank") String serviceName,
            String environment,
            @NotBlank(message = "version cannot be blank") String version,
            @NotBlank(message = "commitSha cannot be blank") String commitSha,
            String commitMessage,
            String deployedBy,
            String status,
            Instant deployedAt,
            String metadata
    ) {}

    @PostMapping
    public ResponseEntity<ApiResponse<DeploymentEntity>> recordDeployment(
            @Valid @RequestBody CreateDeploymentRequest request) {
        requirePermission("record deployment");
        UUID tenantId = TenantContextHolder.getRequiredTenantId();

        UUID serviceId = serviceRepository.findByTenantIdAndName(tenantId, request.serviceName())
                .map(ServiceEntity::getId)
                .orElse(null);

        DeploymentEntity deployment = new DeploymentEntity(
                tenantId,
                null,
                serviceId,
                request.serviceName(),
                request.environment() != null ? request.environment() : "production",
                request.version(),
                request.commitSha(),
                request.commitMessage(),
                request.deployedBy() != null ? request.deployedBy() : "ci-deployer",
                request.status() != null ? request.status() : "SUCCESS",
                request.deployedAt() != null ? request.deployedAt() : Instant.now(),
                request.metadata() != null ? request.metadata() : "{}"
        );

        DeploymentEntity saved = deploymentRepository.save(deployment);
        auditLogService.recordCurrentContext(
                "DEPLOYMENT_RECORDED",
                "deployments/" + saved.getId(),
                null,
                "version=" + saved.getVersion() + ", service=" + saved.getServiceName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(saved));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeploymentEntity>>> listDeployments(
            @RequestParam(value = "service", required = false) String serviceName) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<DeploymentEntity> deployments;
        if (serviceName != null && !serviceName.isBlank()) {
            deployments = deploymentRepository.findByTenantIdAndServiceNameOrderByDeployedAtDesc(tenantId, serviceName);
        } else {
            deployments = deploymentRepository.findByTenantIdAndDeployedAtAfterOrderByDeployedAtDesc(
                    tenantId, Instant.now().minusSeconds(86400 * 7));
        }
        return ResponseEntity.ok(ApiResponse.ok(deployments));
    }

    private void requirePermission(String action) {
        Role role = TenantContextHolder.getContext().map(TenantContext::role).orElse(Role.VIEWER);
        if (role.isReadOnly()) {
            throw new ForbiddenException("Role " + role + " is not authorized to " + action);
        }
    }
}
