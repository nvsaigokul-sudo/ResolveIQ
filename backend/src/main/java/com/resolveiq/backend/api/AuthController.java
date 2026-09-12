package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.UserEntity;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.UserRepository;
import com.resolveiq.backend.dto.auth.CustomerRegistrationRequest;
import com.resolveiq.backend.dto.auth.CustomerRegistrationResponse;
import com.resolveiq.backend.dto.auth.OtpRequest;
import com.resolveiq.backend.dto.auth.OtpResponse;
import com.resolveiq.backend.dto.auth.OtpVerifyRequest;
import com.resolveiq.backend.dto.auth.VerifyEmailRequest;
import com.resolveiq.backend.dto.auth.VerifyEmailResponse;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.backend.service.CustomerAuthService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.exception.UnauthorizedException;
import com.resolveiq.common.security.JwtTokenUtil;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication and Session REST controller (PRD §11.2, §12.1, §35, §38).
 * Issues short-lived cryptographic JWT access tokens carrying tenant_id, user_id, and role claims.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final JwtTokenUtil jwtTokenUtil;
    private final AuditLogService auditLogService;
    private final CustomerAuthService customerAuthService;

    public AuthController(OrganizationRepository organizationRepository,
                          UserRepository userRepository,
                          JwtTokenUtil jwtTokenUtil,
                          AuditLogService auditLogService,
                          CustomerAuthService customerAuthService) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.jwtTokenUtil = jwtTokenUtil;
        this.auditLogService = auditLogService;
        this.customerAuthService = customerAuthService;
    }

    public record LoginRequest(
            @NotBlank(message = "Email cannot be blank") @Email String email,
            String password,
            UUID tenantId,
            Role role
    ) {}

    public record AuthResponse(
            String accessToken,
            String tokenType,
            UUID tenantId,
            UUID userId,
            String email,
            String fullName,
            Role role,
            String organizationName,
            String organizationSlug
    ) {}

    public record SwitchRoleRequest(
            Role role
    ) {}

    public record TenantSummaryDto(
            UUID id,
            String name,
            String slug,
            String planTier
    ) {}

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<TenantSummaryDto>> listTenants() {
        List<TenantSummaryDto> tenants = organizationRepository.findAll().stream()
                .map(org -> new TenantSummaryDto(org.getId(), org.getName(), org.getSlug(), org.getPlanTier()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ofItems(tenants, null, (long) tenants.size()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // 1. Resolve Organization/Tenant
        OrganizationEntity org;
        if (request.tenantId() != null) {
            org = organizationRepository.findById(request.tenantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + request.tenantId()));
        } else {
            org = organizationRepository.findAll().stream().findFirst().orElseGet(() ->
                    organizationRepository.save(new OrganizationEntity("Acme Corp Production", "acme-corp", "ENTERPRISE"))
            );
        }

        // 2. Resolve User within Tenant
        Role targetRole = request.role() != null ? request.role() : Role.SRE;
        Optional<UserEntity> existingUser = userRepository.findByTenantIdAndEmail(org.getId(), request.email().toLowerCase());
        UserEntity user;

        if (existingUser.isPresent()) {
            user = existingUser.get();
            if (request.role() != null && user.getRole() != request.role()) {
                user.setRole(request.role());
                user = userRepository.save(user);
            }
        } else {
            String nameFromEmail = request.email().split("@")[0].replace(".", " ");
            nameFromEmail = Character.toUpperCase(nameFromEmail.charAt(0)) + nameFromEmail.substring(1);
            user = new UserEntity(
                    org.getId(),
                    request.email().toLowerCase(),
                    nameFromEmail,
                    targetRole,
                    null
            );
            user = userRepository.save(user);
        }

        // 3. Issue cryptographic JWT access token
        String token = jwtTokenUtil.generateAccessToken(org.getId(), user.getId(), user.getEmail(), user.getRole());

        try {
            auditLogService.record(
                    org.getId(),
                    user.getId(),
                    "USER",
                    "USER_LOGGED_IN",
                    "user:" + user.getId(),
                    null,
                    String.format("email=%s, role=%s, tenant=%s", user.getEmail(), user.getRole(), org.getName()),
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("Failed to record login audit log: {}", ex.getMessage());
        }

        return ResponseEntity.ok(new AuthResponse(
                token,
                "Bearer",
                org.getId(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                org.getName(),
                org.getSlug()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser() {
        TenantContext ctx = TenantContextHolder.getContext()
                .orElseThrow(() -> new UnauthorizedException("No active authenticated context"));

        OrganizationEntity org = organizationRepository.findById(ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + ctx.tenantId()));

        UserEntity user = null;
        if (ctx.userId() != null) {
            user = userRepository.findById(ctx.userId()).orElse(null);
        }

        String email = user != null ? user.getEmail() : "user@resolveiq.io";
        String fullName = user != null ? user.getFullName() : "On-Call Engineer";
        Role role = ctx.role();

        String token = jwtTokenUtil.generateAccessToken(org.getId(), ctx.userId() != null ? ctx.userId() : UUID.randomUUID(), email, role);

        return ResponseEntity.ok(new AuthResponse(
                token,
                "Bearer",
                org.getId(),
                ctx.userId(),
                email,
                fullName,
                role,
                org.getName(),
                org.getSlug()
        ));
    }

    @PostMapping("/switch-role")
    public ResponseEntity<AuthResponse> switchRole(@RequestBody SwitchRoleRequest request) {
        TenantContext ctx = TenantContextHolder.getContext()
                .orElseThrow(() -> new UnauthorizedException("No active authenticated context"));

        if (request.role() == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }

        OrganizationEntity org = organizationRepository.findById(ctx.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + ctx.tenantId()));

        UserEntity user = null;
        if (ctx.userId() != null) {
            user = userRepository.findById(ctx.userId()).orElse(null);
            if (user != null) {
                user.setRole(request.role());
                user = userRepository.save(user);
            }
        }

        String email = user != null ? user.getEmail() : "engineer@resolveiq.io";
        String fullName = user != null ? user.getFullName() : "On-Call Engineer";
        UUID userId = ctx.userId() != null ? ctx.userId() : UUID.randomUUID();

        // Issue new token with switched role
        String newToken = jwtTokenUtil.generateAccessToken(org.getId(), userId, email, request.role());

        log.info("Role switched to {} for user {} in tenant {}", request.role(), email, org.getId());

        return ResponseEntity.ok(new AuthResponse(
                newToken,
                "Bearer",
                org.getId(),
                userId,
                email,
                fullName,
                request.role(),
                org.getName(),
                org.getSlug()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<CustomerRegistrationResponse> register(@Valid @RequestBody CustomerRegistrationRequest request) {
        CustomerRegistrationResponse response = customerAuthService.registerCustomer(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        VerifyEmailResponse response = customerAuthService.verifyEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/request")
    public ResponseEntity<OtpResponse> requestOtp(@Valid @RequestBody OtpRequest request) {
        OtpResponse response = customerAuthService.requestOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        AuthResponse response = customerAuthService.verifyOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        TenantContextHolder.getContext().ifPresent(ctx -> {
            try {
                auditLogService.record(
                        ctx.tenantId(),
                        ctx.userId() != null ? ctx.userId() : ctx.apiKeyId(),
                        ctx.actorType() != null ? ctx.actorType().name() : "USER",
                        "USER_LOGGED_OUT",
                        "user:" + ctx.userId(),
                        null,
                        "logout successful",
                        null,
                        ctx.traceId()
                );
            } catch (Exception ex) {
                log.warn("Failed to record logout audit log: {}", ex.getMessage());
            }
        });
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
