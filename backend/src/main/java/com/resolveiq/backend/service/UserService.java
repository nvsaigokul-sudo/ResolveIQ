package com.resolveiq.backend.service;

import com.resolveiq.backend.domain.UserEntity;
import com.resolveiq.backend.repository.UserRepository;
import com.resolveiq.common.crypto.Argon2PasswordEncoderUtil;
import com.resolveiq.common.exception.ForbiddenException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public UserService(UserRepository userRepository, AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserEntity createUser(UUID tenantId, String email, String fullName, Role role, String rawPassword) {
        if (userRepository.findByTenantIdAndEmail(tenantId, email).isPresent()) {
            throw new ValidationException("User with email already exists in organization: " + email);
        }

        String passwordHash = rawPassword != null ? Argon2PasswordEncoderUtil.hash(rawPassword) : null;
        UserEntity user = new UserEntity(tenantId, email, fullName, role, passwordHash);
        UserEntity saved = userRepository.save(user);

        TenantContext ctx = TenantContextHolder.getContext().orElse(null);
        if (ctx != null) {
            auditLogService.record(
                    tenantId,
                    ctx.userId(),
                    ctx.actorType().name(),
                    "USER_CREATED",
                    "user:" + saved.getId(),
                    null,
                    "created user: " + email + " with role " + role,
                    null,
                    ctx.traceId()
            );
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public UserEntity getUserForCurrentTenant(UUID userId) {
        return userRepository.findByIdForCurrentTenant(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    @Transactional(readOnly = true)
    public List<UserEntity> listUsersForCurrentTenant() {
        return userRepository.findAllForCurrentTenant();
    }

    @Transactional
    public UserEntity updateUserRole(UUID userId, Role newRole) {
        TenantContext ctx = TenantContextHolder.getRequiredContext();
        if (!ctx.role().canManageUsers()) {
            throw new ForbiddenException("Principal does not have permission to modify user roles");
        }

        UserEntity user = userRepository.findByIdForCurrentTenant(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Role oldRole = user.getRole();
        user.setRole(newRole);
        UserEntity saved = userRepository.save(user);

        auditLogService.record(
                ctx.tenantId(),
                ctx.userId(),
                ctx.actorType().name(),
                "USER_ROLE_CHANGED",
                "user:" + user.getId(),
                "role:" + oldRole,
                "role:" + newRole,
                null,
                ctx.traceId()
        );

        return saved;
    }
}
