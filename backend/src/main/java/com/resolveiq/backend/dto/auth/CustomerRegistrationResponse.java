package com.resolveiq.backend.dto.auth;

import com.resolveiq.backend.domain.RegistrationStatus;

import java.util.UUID;

public record CustomerRegistrationResponse(
        UUID id,
        String email,
        String fullName,
        String companyName,
        RegistrationStatus status,
        String message,
        String verificationToken
) {}
