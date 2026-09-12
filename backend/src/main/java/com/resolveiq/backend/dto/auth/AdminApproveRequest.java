package com.resolveiq.backend.dto.auth;

import com.resolveiq.common.security.Role;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminApproveRequest(
        @NotNull(message = "Target tenant ID is required")
        UUID tenantId,

        @NotNull(message = "Assigned role is required")
        Role role
) {}
