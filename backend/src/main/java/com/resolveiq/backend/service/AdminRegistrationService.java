package com.resolveiq.backend.service;

import com.resolveiq.backend.domain.CustomerRegistrationEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.RegistrationStatus;
import com.resolveiq.backend.domain.UserEntity;
import com.resolveiq.backend.dto.auth.AdminApproveRequest;
import com.resolveiq.backend.dto.auth.AdminRejectRequest;
import com.resolveiq.backend.dto.auth.CustomerRegistrationDto;
import com.resolveiq.backend.repository.CustomerRegistrationRepository;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.UserRepository;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing internal administrator review of customer registrations (PRD Sections 11, 12, 35, 38).
 * Enforces server-side tenant and role assignment: customers NEVER select or control their tenant or role.
 */
@Service
public class AdminRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(AdminRegistrationService.class);

    private final CustomerRegistrationRepository customerRegistrationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public AdminRegistrationService(CustomerRegistrationRepository customerRegistrationRepository,
                                    OrganizationRepository organizationRepository,
                                    UserRepository userRepository,
                                    AuditLogService auditLogService) {
        this.customerRegistrationRepository = customerRegistrationRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<CustomerRegistrationDto> listRegistrations(RegistrationStatus status) {
        List<CustomerRegistrationEntity> entities;
        if (status != null) {
            entities = customerRegistrationRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            entities = customerRegistrationRepository.findAllByOrderByCreatedAtDesc();
        }
        return entities.stream().map(CustomerRegistrationDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public CustomerRegistrationDto getRegistration(UUID id) {
        CustomerRegistrationEntity entity = customerRegistrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer registration not found: " + id));
        return CustomerRegistrationDto.fromEntity(entity);
    }

    @Transactional
    public CustomerRegistrationDto approveRegistration(UUID id, AdminApproveRequest request, UUID adminUserId) {
        CustomerRegistrationEntity entity = customerRegistrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer registration not found: " + id));

        if (entity.getStatus() == RegistrationStatus.PENDING_EMAIL_VERIFICATION) {
            throw new ValidationException("Cannot approve registration before email verification has completed.");
        }

        OrganizationEntity org = organizationRepository.findById(request.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Target organization not found: " + request.tenantId()));

        Role assignedRole = request.role() != null ? request.role() : Role.VIEWER;

        String beforeState = entity.getStatus().name();
        entity.setAssignedTenantId(org.getId());
        entity.setAssignedRole(assignedRole);
        entity.setStatus(RegistrationStatus.ACTIVE);
        entity.setReviewedBy(adminUserId);
        entity.setReviewedAt(Instant.now());
        entity.setRejectionReason(null);
        entity.setUpdatedAt(Instant.now());

        entity = customerRegistrationRepository.save(entity);

        // Provision or synchronize UserEntity within target tenant
        Optional<UserEntity> userOpt = userRepository.findByTenantIdAndEmail(org.getId(), entity.getEmail());
        UserEntity user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            user.setRole(assignedRole);
            user.setStatus("ACTIVE");
            user = userRepository.save(user);
        } else {
            user = new UserEntity(
                    org.getId(),
                    entity.getEmail(),
                    entity.getFullName(),
                    assignedRole,
                    null
            );
            user = userRepository.save(user);
        }

        try {
            auditLogService.record(
                    org.getId(),
                    adminUserId,
                    "USER",
                    "CUSTOMER_REGISTRATION_APPROVED",
                    "customer_registration:" + entity.getId(),
                    beforeState,
                    String.format("status=ACTIVE, tenant=%s, role=%s, user=%s", org.getId(), assignedRole, user.getId()),
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to write audit log for registration approval: {}", ex.getMessage());
        }

        log.info("Admin {} approved customer registration {} into tenant {} with role {}",
                adminUserId, entity.getEmail(), org.getId(), assignedRole);

        return CustomerRegistrationDto.fromEntity(entity);
    }

    @Transactional
    public CustomerRegistrationDto rejectRegistration(UUID id, AdminRejectRequest request, UUID adminUserId) {
        CustomerRegistrationEntity entity = customerRegistrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer registration not found: " + id));

        String beforeState = entity.getStatus().name();
        entity.setStatus(RegistrationStatus.REJECTED);
        entity.setRejectionReason(request != null ? request.reason() : "Application criteria not met");
        entity.setReviewedBy(adminUserId);
        entity.setReviewedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        entity = customerRegistrationRepository.save(entity);

        UUID tenantId = entity.getAssignedTenantId() != null ? entity.getAssignedTenantId() : resolveFallbackTenantId();
        try {
            auditLogService.record(
                    tenantId,
                    adminUserId,
                    "USER",
                    "CUSTOMER_REGISTRATION_REJECTED",
                    "customer_registration:" + entity.getId(),
                    beforeState,
                    "REJECTED: " + entity.getRejectionReason(),
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to write audit log for registration rejection: {}", ex.getMessage());
        }

        log.info("Admin {} rejected customer registration {} (reason: {})",
                adminUserId, entity.getEmail(), entity.getRejectionReason());

        return CustomerRegistrationDto.fromEntity(entity);
    }

    @Transactional
    public CustomerRegistrationDto suspendRegistration(UUID id, UUID adminUserId) {
        CustomerRegistrationEntity entity = customerRegistrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer registration not found: " + id));

        entity.setStatus(RegistrationStatus.SUSPENDED);
        entity.setUpdatedAt(Instant.now());
        entity = customerRegistrationRepository.save(entity);

        if (entity.getAssignedTenantId() != null) {
            userRepository.findByTenantIdAndEmail(entity.getAssignedTenantId(), entity.getEmail())
                    .ifPresent(user -> {
                        user.setStatus("SUSPENDED");
                        userRepository.save(user);
                    });
        }

        UUID tenantId = entity.getAssignedTenantId() != null ? entity.getAssignedTenantId() : resolveFallbackTenantId();
        try {
            auditLogService.record(
                    tenantId,
                    adminUserId,
                    "USER",
                    "CUSTOMER_ACCOUNT_SUSPENDED",
                    "customer_registration:" + entity.getId(),
                    "ACTIVE",
                    "SUSPENDED",
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to record suspension audit log: {}", ex.getMessage());
        }

        return CustomerRegistrationDto.fromEntity(entity);
    }

    @Transactional
    public CustomerRegistrationDto activateRegistration(UUID id, UUID adminUserId) {
        CustomerRegistrationEntity entity = customerRegistrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer registration not found: " + id));

        if (entity.getAssignedTenantId() == null || entity.getAssignedRole() == null) {
            throw new ValidationException("Cannot activate customer without assigned organization and role.");
        }

        entity.setStatus(RegistrationStatus.ACTIVE);
        entity.setUpdatedAt(Instant.now());
        entity = customerRegistrationRepository.save(entity);

        userRepository.findByTenantIdAndEmail(entity.getAssignedTenantId(), entity.getEmail())
                .ifPresent(user -> {
                    user.setStatus("ACTIVE");
                    userRepository.save(user);
                });

        try {
            auditLogService.record(
                    entity.getAssignedTenantId(),
                    adminUserId,
                    "USER",
                    "CUSTOMER_ACCOUNT_ACTIVATED",
                    "customer_registration:" + entity.getId(),
                    "SUSPENDED",
                    "ACTIVE",
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to record activation audit log: {}", ex.getMessage());
        }

        return CustomerRegistrationDto.fromEntity(entity);
    }

    private UUID resolveFallbackTenantId() {
        return organizationRepository.findAll().stream()
                .findFirst()
                .map(OrganizationEntity::getId)
                .orElseGet(() -> {
                    OrganizationEntity defaultOrg = organizationRepository.save(
                            new OrganizationEntity("ResolveIQ System", "resolveiq-system", "ENTERPRISE")
                    );
                    return defaultOrg.getId();
                });
    }
}
