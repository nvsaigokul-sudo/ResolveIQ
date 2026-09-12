package com.resolveiq.backend.dto.auth;

import com.resolveiq.backend.domain.RegistrationStatus;

import java.util.UUID;

public record VerifyEmailResponse(
        UUID id,
        String email,
        RegistrationStatus status,
        String message
) {}
