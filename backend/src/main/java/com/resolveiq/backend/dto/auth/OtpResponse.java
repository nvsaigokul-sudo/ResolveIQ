package com.resolveiq.backend.dto.auth;

public record OtpResponse(
        String email,
        String message,
        long expiresInSeconds,
        String devOtp
) {}
