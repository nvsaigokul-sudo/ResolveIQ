package com.resolveiq.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "Verification token cannot be blank")
        String token
) {}
