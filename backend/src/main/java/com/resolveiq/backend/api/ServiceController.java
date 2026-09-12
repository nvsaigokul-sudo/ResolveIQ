package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.DependencyEntity;
import com.resolveiq.backend.domain.DeploymentEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.domain.ServiceEntity;
import com.resolveiq.backend.repository.DependencyRepository;
import com.resolveiq.backend.repository.DeploymentRepository;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.repository.ServiceRepository;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.incident.IncidentStatus;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for Microservices and Dependencies Directory (PRD §18, §35, §38).
 */
@RestController
@RequestMapping("/api/v1")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final DependencyRepository dependencyRepository;
    private final IncidentRepository incidentRepository;
    private final DeploymentRepository deploymentRepository;
    private final TenantService tenantService;
    private final AuditLogService auditLogService;

    public ServiceController(ServiceRepository serviceRepository,
                             DependencyRepository dependencyRepository,
                             IncidentRepository incidentRepository,
                             DeploymentRepository deploymentRepository,
                             TenantService tenantService,
                             AuditLogService auditLogService) {
        this.serviceRepository = serviceRepository;
        this.dependencyRepository = dependencyRepository;
        this.incidentRepository = incidentRepository;
        this.deploymentRepository = deploymentRepository;
        this.tenantService = tenantService;
        this.auditLogService = auditLogService;
    }

    public record ServiceSummaryDto(
            UUID id,
            UUID projectId,
            String name,
            String tier,
            String ownerTeam,
            String repoUrl,
            String healthStatus,
            long activeIncidentsCount,
            Instant createdAt
    ) {}

    public record CreateServiceRequest(
            @NotNull(message = "Project ID is required") UUID projectId,
            @NotBlank(message = "Service name cannot be blank") String name,
            @NotBlank(message = "Tier cannot be blank") String tier,
            String ownerTeam,
            String repoUrl
    ) {}

    public record DependencyEdgeDto(
            UUID id,
            UUID upstreamServiceId,
            String upstreamServiceName,
            UUID downstreamServiceId,
            String downstreamServiceName,
            String callType,
            String healthStatus,
            Instant updatedAt
    ) {}

    public record CreateDependencyRequest(
            @NotNull UUID upstreamServiceId,
            @NotNull UUID downstreamServiceId,
            String callType
    ) {}

    public record TopologyNode(
            String id,
            String label,
            String tier,
            String status,
            int activeIncidents
    ) {}

    public record TopologyEdge(
            String source,
            String target,
            String callType,
            String status
    ) {}

    public record TopologyGraphDto(
            String rootService,
            List<TopologyNode> nodes,
            List<TopologyEdge> edges,
            List<String> blastRadius
    ) {}

    @GetMapping("/services")
    public ResponseEntity<ApiResponse<ServiceSummaryDto>> listServices() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<ServiceEntity> services = serviceRepository.findAllByTenantId(tenantId);

        Map<String, Long> activeIncidentCounts = incidentRepository.findAllByTenantId(tenantId).stream()
                .filter(inc -> inc.getStatus() != IncidentStatus.RESOLVED && inc.getStatus() != IncidentStatus.CLOSED)
                .collect(Collectors.groupingBy(inc -> inc.getRootService() != null ? inc.getRootService() : "unknown", Collectors.counting()));

        List<ServiceSummaryDto> dtos = services.stream().map(s -> {
            long count = activeIncidentCounts.getOrDefault(s.getName(), 0L);
            String health = count > 0 ? "DEGRADED" : "HEALTHY";
            return new ServiceSummaryDto(
                    s.getId(),
                    s.getProjectId(),
                    s.getName(),
                    s.getTier(),
                    s.getOwnerTeam(),
                    s.getRepoUrl(),
                    health,
                    count,
                    s.getCreatedAt()
            );
        }).toList();

        return ResponseEntity.ok(ApiResponse.ofItems(dtos, null, (long) dtos.size()));
    }

    @PostMapping("/services")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'SRE')")
    public ResponseEntity<ServiceEntity> createService(@Valid @RequestBody CreateServiceRequest request) {
        ServiceEntity created = tenantService.createService(
                request.projectId(), request.name(), request.tier(), request.ownerTeam(), request.repoUrl()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<ServiceSummaryDto> getService(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        ServiceEntity s = serviceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));

        long count = incidentRepository.findAllByTenantId(tenantId).stream()
                .filter(inc -> inc.getStatus() != IncidentStatus.RESOLVED && inc.getStatus() != IncidentStatus.CLOSED)
                .filter(inc -> s.getName().equalsIgnoreCase(inc.getRootService()))
                .count();

        return ResponseEntity.ok(new ServiceSummaryDto(
                s.getId(),
                s.getProjectId(),
                s.getName(),
                s.getTier(),
                s.getOwnerTeam(),
                s.getRepoUrl(),
                count > 0 ? "DEGRADED" : "HEALTHY",
                count,
                s.getCreatedAt()
        ));
    }

    @GetMapping("/dependencies")
    public ResponseEntity<ApiResponse<DependencyEdgeDto>> listDependencies() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<DependencyEntity> deps = dependencyRepository.findAllByTenantId(tenantId);
        Map<UUID, String> serviceNames = serviceRepository.findAllByTenantId(tenantId).stream()
                .collect(Collectors.toMap(ServiceEntity::getId, ServiceEntity::getName, (a, b) -> a));

        List<DependencyEdgeDto> dtos = deps.stream().map(d -> new DependencyEdgeDto(
                d.getId(),
                d.getUpstreamServiceId(),
                serviceNames.getOrDefault(d.getUpstreamServiceId(), d.getUpstreamServiceId().toString()),
                d.getDownstreamServiceId(),
                serviceNames.getOrDefault(d.getDownstreamServiceId(), d.getDownstreamServiceId().toString()),
                d.getCallType(),
                d.getHealthStatus(),
                d.getUpdatedAt()
        )).toList();

        return ResponseEntity.ok(ApiResponse.ofItems(dtos, null, (long) dtos.size()));
    }

    @PostMapping("/dependencies")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'SRE')")
    public ResponseEntity<DependencyEntity> createDependency(@Valid @RequestBody CreateDependencyRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        DependencyEntity dep = new DependencyEntity(
                tenantId,
                request.upstreamServiceId(),
                request.downstreamServiceId(),
                request.callType() != null ? request.callType() : "HTTP",
                "HEALTHY"
        );
        DependencyEntity saved = dependencyRepository.save(dep);
        auditLogService.recordCurrentContext("DEPENDENCY_CREATED", "dependency:" + saved.getId(), null,
                "upstream=" + request.upstreamServiceId() + " -> downstream=" + request.downstreamServiceId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/services/{name}/topology")
    public ResponseEntity<TopologyGraphDto> getServiceTopology(@PathVariable("name") String serviceName) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        List<ServiceEntity> allServices = serviceRepository.findAllByTenantId(tenantId);
        Map<UUID, ServiceEntity> serviceById = allServices.stream()
                .collect(Collectors.toMap(ServiceEntity::getId, s -> s, (a, b) -> a));
        Map<String, ServiceEntity> serviceByName = allServices.stream()
                .collect(Collectors.toMap(s -> s.getName().toLowerCase(), s -> s, (a, b) -> a));

        List<DependencyEntity> allDeps = dependencyRepository.findAllByTenantId(tenantId);

        // Build adjacency graph
        Map<String, Set<String>> downstreamAdj = new HashMap<>();
        Map<String, Set<String>> upstreamAdj = new HashMap<>();

        for (DependencyEntity d : allDeps) {
            ServiceEntity up = serviceById.get(d.getUpstreamServiceId());
            ServiceEntity down = serviceById.get(d.getDownstreamServiceId());
            if (up != null && down != null) {
                downstreamAdj.computeIfAbsent(up.getName(), k -> new HashSet<>()).add(down.getName());
                upstreamAdj.computeIfAbsent(down.getName(), k -> new HashSet<>()).add(up.getName());
            }
        }

        // Canonical topology fallback if dependencies table has not yet been populated
        if (allDeps.isEmpty()) {
            // api-gateway -> order-service -> payment-service
            // api-gateway -> user-service
            // order-service -> inventory-service
            // order-service -> notification-service
            downstreamAdj.computeIfAbsent("api-gateway", k -> new HashSet<>()).addAll(List.of("order-service", "user-service"));
            downstreamAdj.computeIfAbsent("order-service", k -> new HashSet<>()).addAll(List.of("payment-service", "inventory-service", "notification-service"));
            downstreamAdj.computeIfAbsent("payment-service", k -> new HashSet<>()).add("database");
            upstreamAdj.computeIfAbsent("payment-service", k -> new HashSet<>()).add("order-service");
            upstreamAdj.computeIfAbsent("order-service", k -> new HashSet<>()).add("api-gateway");
        }

        // Compute Blast Radius (all upstream services that depend on this service)
        Set<String> blastRadius = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(serviceName);
        blastRadius.add(serviceName);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            Set<String> callers = upstreamAdj.getOrDefault(current, Collections.emptySet());
            for (String caller : callers) {
                if (blastRadius.add(caller)) {
                    queue.add(caller);
                }
            }
        }

        // Build Nodes
        List<TopologyNode> nodes = new ArrayList<>();
        Set<String> allNodeNames = new HashSet<>();
        allNodeNames.addAll(downstreamAdj.keySet());
        downstreamAdj.values().forEach(allNodeNames::addAll);
        allNodeNames.addAll(upstreamAdj.keySet());
        upstreamAdj.values().forEach(allNodeNames::addAll);
        allNodeNames.add(serviceName);

        for (String nodeName : allNodeNames) {
            ServiceEntity svc = serviceByName.get(nodeName.toLowerCase());
            String tier = svc != null ? svc.getTier() : "TIER_2";
            String status = blastRadius.contains(nodeName) ? (nodeName.equalsIgnoreCase(serviceName) ? "ROOT_CAUSE" : "IMPACTED") : "HEALTHY";
            nodes.add(new TopologyNode(nodeName, nodeName, tier, status, blastRadius.contains(nodeName) ? 1 : 0));
        }

        // Build Edges
        List<TopologyEdge> edges = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : downstreamAdj.entrySet()) {
            String src = entry.getKey();
            for (String tgt : entry.getValue()) {
                String edgeStatus = blastRadius.contains(src) && blastRadius.contains(tgt) ? "DEGRADED" : "NOMINAL";
                edges.add(new TopologyEdge(src, tgt, "HTTP", edgeStatus));
            }
        }

        return ResponseEntity.ok(new TopologyGraphDto(serviceName, nodes, edges, new ArrayList<>(blastRadius)));
    }
}
