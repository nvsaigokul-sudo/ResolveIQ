package com.resolveiq.backend.dto.auth;

import com.resolveiq.backend.domain.CustomerRegistrationEntity;
import com.resolveiq.backend.domain.RegistrationStatus;
import com.resolveiq.common.security.Role;

import java.time.Instant;
import java.util.UUID;

public record CustomerRegistrationDto(
        UUID id,
        String email,
        String fullName,
        String companyName,
        String jobTitle,
        RegistrationStatus status,
        Instant emailVerifiedAt,
        UUID assignedTenantId,
        Role assignedRole,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerRegistrationDto fromEntity(CustomerRegistrationEntity entity) {
        return new CustomerRegistrationDto(
                entity.getId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getCompanyName(),
                entity.getJobTitle(),
                entity.getStatus(),
                entity.getEmailVerifiedAt(),
                entity.getAssignedTenantId(),
                entity.getAssignedRole(),
                entity.getReviewedBy(),
                entity.getReviewedAt(),
                entity.getRejectionReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
