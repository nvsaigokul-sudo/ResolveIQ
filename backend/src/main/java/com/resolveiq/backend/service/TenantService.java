package com.resolveiq.backend.service;

import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.domain.ServiceEntity;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.ProjectRepository;
import com.resolveiq.backend.repository.ServiceRepository;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final ServiceRepository serviceRepository;
    private final AuditLogService auditLogService;

    public TenantService(OrganizationRepository organizationRepository,
                         ProjectRepository projectRepository,
                         ServiceRepository serviceRepository,
                         AuditLogService auditLogService) {
        this.organizationRepository = organizationRepository;
        this.projectRepository = projectRepository;
        this.serviceRepository = serviceRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public OrganizationEntity createOrganization(String name, String slug, String planTier) {
        if (organizationRepository.existsBySlug(slug)) {
            throw new ValidationException("Organization slug already exists: " + slug);
        }
        OrganizationEntity org = new OrganizationEntity(name, slug, planTier);
        return organizationRepository.save(org);
    }

    @Transactional(readOnly = true)
    public OrganizationEntity getOrganization(UUID orgId) {
        return organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + orgId));
    }

    @Transactional
    public ProjectEntity createProject(String name, String slug, String description) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        if (projectRepository.findByTenantIdAndSlug(tenantId, slug).isPresent()) {
            throw new ValidationException("Project slug already exists in this tenant: " + slug);
        }
        ProjectEntity project = new ProjectEntity(tenantId, name, slug, description);
        ProjectEntity saved = projectRepository.save(project);

        auditLogService.recordCurrentContext("PROJECT_CREATED", "project:" + saved.getId(), null, "created: " + name);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ProjectEntity> getProjectsForCurrentTenant() {
        return projectRepository.findAllForCurrentTenant();
    }

    @Transactional(readOnly = true)
    public ProjectEntity getProjectById(UUID projectId) {
        return projectRepository.findByIdForCurrentTenant(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    @Transactional
    public ServiceEntity createService(UUID projectId, String name, String tier, String ownerTeam, String repoUrl) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        // verify project belongs to tenant
        getProjectById(projectId);

        if (serviceRepository.findByTenantIdAndProjectIdAndName(tenantId, projectId, name).isPresent()) {
            throw new ValidationException("Service already exists in this project: " + name);
        }

        ServiceEntity service = new ServiceEntity(tenantId, projectId, name, tier, ownerTeam, repoUrl);
        ServiceEntity saved = serviceRepository.save(service);

        auditLogService.recordCurrentContext("SERVICE_CREATED", "service:" + saved.getId(), null, "created: " + name);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ServiceEntity> getServicesForCurrentTenant() {
        return serviceRepository.findAllForCurrentTenant();
    }
}
