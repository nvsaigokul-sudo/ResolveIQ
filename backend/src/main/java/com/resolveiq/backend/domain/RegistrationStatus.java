package com.resolveiq.backend.domain;

/**
 * Lifecycle states for Customer Registration & Onboarding.
 */
public enum RegistrationStatus {
    PENDING_EMAIL_VERIFICATION,
    EMAIL_VERIFIED,
    PENDING_ADMIN_REVIEW,
    APPROVED,
    ACTIVE,
    REJECTED,
    SUSPENDED,
    DEACTIVATED
}
