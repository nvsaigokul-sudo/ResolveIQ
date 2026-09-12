package com.resolveiq.backend.dto.auth;

import jakarta.validation.constraints.Size;

public record AdminRejectRequest(
        @Size(max = 1000, message = "Rejection reason must not exceed 1000 characters")
        String reason
) {}
