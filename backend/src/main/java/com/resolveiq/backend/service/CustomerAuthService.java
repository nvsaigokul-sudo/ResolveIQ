package com.resolveiq.backend.service;

import com.resolveiq.backend.api.AuthController.AuthResponse;
import com.resolveiq.backend.domain.AuthOtpEntity;
import com.resolveiq.backend.domain.CustomerRegistrationEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.RegistrationStatus;
import com.resolveiq.backend.domain.UserEntity;
import com.resolveiq.backend.dto.auth.CustomerRegistrationRequest;
import com.resolveiq.backend.dto.auth.CustomerRegistrationResponse;
import com.resolveiq.backend.dto.auth.OtpRequest;
import com.resolveiq.backend.dto.auth.OtpResponse;
import com.resolveiq.backend.dto.auth.OtpVerifyRequest;
import com.resolveiq.backend.dto.auth.VerifyEmailRequest;
import com.resolveiq.backend.dto.auth.VerifyEmailResponse;
import com.resolveiq.backend.repository.AuthOtpRepository;
import com.resolveiq.backend.repository.CustomerRegistrationRepository;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.UserRepository;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.UnauthorizedException;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.security.JwtTokenUtil;
import com.resolveiq.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing customer onboarding lifecycle and passwordless OTP authentication.
 * Implements strict state transitions:
 * PENDING_EMAIL_VERIFICATION -> EMAIL_VERIFIED -> PENDING_ADMIN_REVIEW -> APPROVED -> ACTIVE.
 * Customer authentication is 100% passwordless via single-use 6-digit OTP (5-minute TTL, max 5 attempts lockout).
 */
@Service
public class CustomerAuthService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAuthService.class);

    private final CustomerRegistrationRepository customerRegistrationRepository;
    private final AuthOtpRepository authOtpRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final JwtTokenUtil jwtTokenUtil;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public CustomerAuthService(CustomerRegistrationRepository customerRegistrationRepository,
                               AuthOtpRepository authOtpRepository,
                               OrganizationRepository organizationRepository,
                               UserRepository userRepository,
                               JwtTokenUtil jwtTokenUtil,
                               AuditLogService auditLogService) {
        this.customerRegistrationRepository = customerRegistrationRepository;
        this.authOtpRepository = authOtpRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.jwtTokenUtil = jwtTokenUtil;
        this.auditLogService = auditLogService;
    }

    /**
     * Registers a new customer prospect.
     * Generates a single-use 64-char hex verification token hashed with SHA-256 in DB.
     */
    @Transactional
    public CustomerRegistrationResponse registerCustomer(CustomerRegistrationRequest request) {
        String email = request.email().toLowerCase().trim();
        String fullName = request.fullName().trim();
        String companyName = request.companyName().trim();
        String jobTitle = request.jobTitle() != null ? request.jobTitle().trim() : null;

        Optional<CustomerRegistrationEntity> existingOpt = customerRegistrationRepository.findByEmailIgnoreCase(email);
        CustomerRegistrationEntity entity;

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = HexFormat.of().formatHex(tokenBytes);
        String tokenHash = hashSha256(rawToken);
        Instant tokenExpiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            if (entity.getStatus() == RegistrationStatus.PENDING_EMAIL_VERIFICATION) {
                // Re-issuing verification token
                entity.setFullName(fullName);
                entity.setCompanyName(companyName);
                entity.setJobTitle(jobTitle);
                entity.setVerificationTokenHash(tokenHash);
                entity.setVerificationTokenExpiresAt(tokenExpiresAt);
                entity.setUpdatedAt(Instant.now());
                entity = customerRegistrationRepository.save(entity);
            } else if (entity.getStatus() == RegistrationStatus.PENDING_ADMIN_REVIEW) {
                throw new ValidationException("An account with this email is currently pending administrator review.");
            } else if (entity.getStatus() == RegistrationStatus.APPROVED || entity.getStatus() == RegistrationStatus.ACTIVE) {
                throw new ValidationException("An account with this email is already active. Please sign in with OTP.");
            } else if (entity.getStatus() == RegistrationStatus.REJECTED) {
                throw new ValidationException("Registration with this email was rejected. Please contact support.");
            } else {
                throw new ValidationException("Account status is " + entity.getStatus() + ". Please contact support.");
            }
        } else {
            entity = new CustomerRegistrationEntity(
                    email,
                    fullName,
                    companyName,
                    jobTitle,
                    tokenHash,
                    tokenExpiresAt
            );
            entity = customerRegistrationRepository.save(entity);
        }

        try {
            UUID auditTenantId = resolveFallbackTenantId();
            auditLogService.record(
                    auditTenantId,
                    null,
                    "CUSTOMER",
                    "CUSTOMER_REGISTRATION_SUBMITTED",
                    "customer_registration:" + entity.getId(),
                    null,
                    String.format("email=%s, company=%s", email, companyName),
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to write audit log for registration: {}", ex.getMessage());
        }

        log.info("Customer registration created for email {} with status {}", email, entity.getStatus());

        return new CustomerRegistrationResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getCompanyName(),
                entity.getStatus(),
                "Registration submitted successfully. Please verify your email to continue.",
                rawToken
        );
    }

    /**
     * Verifies the customer's email using the single-use token.
     * Transitions registration state from PENDING_EMAIL_VERIFICATION -> PENDING_ADMIN_REVIEW.
     * CRITICAL SECURITY INVARIANT: Email verification DOES NOT grant dashboard/data access or issue a JWT.
     */
    @Transactional
    public VerifyEmailResponse verifyEmail(VerifyEmailRequest request) {
        String rawToken = request.token().trim();
        String tokenHash = hashSha256(rawToken);

        CustomerRegistrationEntity entity = customerRegistrationRepository.findByVerificationTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException("Invalid or expired email verification token."));

        if (entity.getStatus() != RegistrationStatus.PENDING_EMAIL_VERIFICATION) {
            if (entity.getStatus() == RegistrationStatus.PENDING_ADMIN_REVIEW) {
                return new VerifyEmailResponse(
                        entity.getId(),
                        entity.getEmail(),
                        entity.getStatus(),
                        "Email is already verified. Registration is currently awaiting administrator review."
                );
            }
            throw new ValidationException("Registration cannot be verified in current status: " + entity.getStatus());
        }

        if (entity.getVerificationTokenExpiresAt() != null && entity.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("Verification token has expired. Please register again to request a new link.");
        }

        entity.setStatus(RegistrationStatus.PENDING_ADMIN_REVIEW);
        entity.setEmailVerifiedAt(Instant.now());
        entity.setVerificationTokenHash(null); // Single use
        entity.setVerificationTokenExpiresAt(null);
        entity.setUpdatedAt(Instant.now());
        entity = customerRegistrationRepository.save(entity);

        try {
            UUID auditTenantId = resolveFallbackTenantId();
            auditLogService.record(
                    auditTenantId,
                    null,
                    "CUSTOMER",
                    "CUSTOMER_EMAIL_VERIFIED",
                    "customer_registration:" + entity.getId(),
                    "PENDING_EMAIL_VERIFICATION",
                    "PENDING_ADMIN_REVIEW",
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to write audit log for email verification: {}", ex.getMessage());
        }

        log.info("Customer email verified for {}. Advanced to PENDING_ADMIN_REVIEW", entity.getEmail());

        return new VerifyEmailResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getStatus(),
                "Email verified successfully. Your registration is now pending review by a ResolveIQ administrator."
        );
    }

    /**
     * Requests a single-use 6-digit OTP for approved/active customers.
     * Enforces:
     * - Status must be APPROVED or ACTIVE
     * - 60s cooldown between requests
     * - Rate limit: max 5 requests per 15 minutes
     * - 5-minute TTL
     */
    @Transactional
    public OtpResponse requestOtp(OtpRequest request) {
        String email = request.email().toLowerCase().trim();

        CustomerRegistrationEntity registration = customerRegistrationRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ValidationException("No registered account found for email: " + email));

        if (registration.getStatus() == RegistrationStatus.PENDING_EMAIL_VERIFICATION) {
            throw new ValidationException("Please verify your email address before signing in.");
        }
        if (registration.getStatus() == RegistrationStatus.PENDING_ADMIN_REVIEW) {
            throw new ValidationException("Your account is awaiting administrator review. You will be notified once approved.");
        }
        if (registration.getStatus() == RegistrationStatus.REJECTED) {
            throw new ValidationException("Account registration was rejected. Reason: " +
                    (registration.getRejectionReason() != null ? registration.getRejectionReason() : "Please contact support."));
        }
        if (registration.getStatus() == RegistrationStatus.SUSPENDED || registration.getStatus() == RegistrationStatus.DEACTIVATED) {
            throw new ValidationException("Account has been " + registration.getStatus().name().toLowerCase() + ". Please contact support.");
        }
        if (registration.getStatus() != RegistrationStatus.APPROVED && registration.getStatus() != RegistrationStatus.ACTIVE) {
            throw new ValidationException("Account is not active (status: " + registration.getStatus() + ")");
        }
        if (registration.getAssignedTenantId() == null || registration.getAssignedRole() == null) {
            throw new ValidationException("Account has not yet been assigned to an organization or role. Please contact support.");
        }

        // Cooldown check (60 seconds)
        Optional<AuthOtpEntity> latestOtpOpt = authOtpRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (latestOtpOpt.isPresent()) {
            Instant latestCreatedAt = latestOtpOpt.get().getCreatedAt();
            if (latestCreatedAt.isAfter(Instant.now().minus(60, ChronoUnit.SECONDS))) {
                long waitSec = 60 - ChronoUnit.SECONDS.between(latestCreatedAt, Instant.now());
                throw new ValidationException("Please wait " + Math.max(1, waitSec) + " seconds before requesting a new OTP.");
            }
        }

        // Rate limit check: max 5 in 15 minutes
        long countLast15Min = authOtpRepository.countByEmailIgnoreCaseAndCreatedAtAfter(email, Instant.now().minus(15, ChronoUnit.MINUTES));
        if (countLast15Min >= 5) {
            throw new ValidationException("Too many OTP requests. Maximum 5 requests per 15 minutes. Please try again later.");
        }

        // Generate 6-digit numeric OTP
        int code = secureRandom.nextInt(1_000_000);
        String rawOtp = String.format("%06d", code);
        String otpHash = hashSha256(rawOtp);
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        AuthOtpEntity otpEntity = new AuthOtpEntity(email, otpHash, expiresAt);
        authOtpRepository.save(otpEntity);

        try {
            auditLogService.record(
                    registration.getAssignedTenantId(),
                    null,
                    "CUSTOMER",
                    "OTP_REQUESTED",
                    "email:" + email,
                    null,
                    "expires_in=300s",
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to record OTP request audit log: {}", ex.getMessage());
        }

        log.info("OTP generated for customer email: {}", email);

        return new OtpResponse(
                email,
                "A 6-digit verification code has been sent to your email address.",
                300L,
                rawOtp // included for testing/dev environments
        );
    }

    /**
     * Verifies the OTP and issues a cryptographic JWT.
     * Enforces single-use, 5-minute TTL, and max 5 attempts lockout.
     */
    @Transactional(noRollbackFor = ValidationException.class)
    public AuthResponse verifyOtp(OtpVerifyRequest request) {
        String email = request.email().toLowerCase().trim();
        String submittedOtp = request.otp().trim();

        CustomerRegistrationEntity registration = customerRegistrationRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ValidationException("Account not found for email: " + email));

        if (registration.getStatus() != RegistrationStatus.APPROVED && registration.getStatus() != RegistrationStatus.ACTIVE) {
            throw new ValidationException("Account is not active (status: " + registration.getStatus() + ")");
        }

        AuthOtpEntity otpEntity = authOtpRepository.findTopByEmailIgnoreCaseAndConsumedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ValidationException("No valid OTP found. Please request a new verification code."));

        if (otpEntity.getAttempts() >= 5) {
            otpEntity.setConsumed(true);
            authOtpRepository.save(otpEntity);
            throw new ValidationException("Maximum verification attempts (5) exceeded. This code has been invalidated. Please request a new code.");
        }

        if (otpEntity.getExpiresAt().isBefore(Instant.now())) {
            otpEntity.setConsumed(true);
            authOtpRepository.save(otpEntity);
            throw new ValidationException("Verification code has expired. Please request a new code.");
        }

        String submittedHash = hashSha256(submittedOtp);
        if (!submittedHash.equals(otpEntity.getOtpHash())) {
            int attempts = otpEntity.getAttempts() + 1;
            otpEntity.setAttempts(attempts);
            if (attempts >= 5) {
                otpEntity.setConsumed(true);
            }
            authOtpRepository.save(otpEntity);
            int remaining = 5 - attempts;
            if (remaining > 0) {
                throw new ValidationException("Invalid verification code. " + remaining + " attempts remaining.");
            } else {
                throw new ValidationException("Maximum verification attempts exceeded. Code has been invalidated.");
            }
        }

        // OTP Validated
        otpEntity.setConsumed(true);
        authOtpRepository.save(otpEntity);

        if (registration.getStatus() == RegistrationStatus.APPROVED) {
            registration.setStatus(RegistrationStatus.ACTIVE);
            registration.setUpdatedAt(Instant.now());
            registration = customerRegistrationRepository.save(registration);
        }

        UUID tenantId = registration.getAssignedTenantId();
        Role role = registration.getAssignedRole();
        final String userFullName = registration.getFullName();

        OrganizationEntity org = organizationRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Assigned organization not found: " + tenantId));

        UserEntity user = userRepository.findByTenantIdAndEmail(tenantId, email)
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity(
                            tenantId,
                            email,
                            userFullName,
                            role,
                            null
                    );
                    return userRepository.save(newUser);
                });

        String token = jwtTokenUtil.generateAccessToken(tenantId, user.getId(), user.getEmail(), role);

        try {
            auditLogService.record(
                    tenantId,
                    user.getId(),
                    "CUSTOMER",
                    "CUSTOMER_LOGGED_IN_OTP",
                    "user:" + user.getId(),
                    null,
                    String.format("email=%s, role=%s, org=%s", email, role, org.getName()),
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to record OTP login audit log: {}", ex.getMessage());
        }

        log.info("Customer {} successfully logged in via OTP with role {} in tenant {}", email, role, tenantId);

        return new AuthResponse(
                token,
                "Bearer",
                tenantId,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                role,
                org.getName(),
                org.getSlug()
        );
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

    public static String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
