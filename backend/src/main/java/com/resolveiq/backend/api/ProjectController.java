package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final TenantService tenantService;

    public ProjectController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public record CreateProjectRequest(
            @NotBlank(message = "Name cannot be blank") String name,
            @NotBlank(message = "Slug cannot be blank") String slug,
            String description
    ) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'SRE')")
    public ResponseEntity<ProjectEntity> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectEntity project = tenantService.createProject(request.name(), request.slug(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProjectEntity>> listProjects() {
        List<ProjectEntity> projects = tenantService.getProjectsForCurrentTenant();
        return ResponseEntity.ok(ApiResponse.ofItems(projects, null, (long) projects.size()));
    }
}
