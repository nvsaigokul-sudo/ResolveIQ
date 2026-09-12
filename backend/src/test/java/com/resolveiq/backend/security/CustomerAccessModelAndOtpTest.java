package com.resolveiq.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.api.AuthController;
import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.dto.auth.*;
import com.resolveiq.backend.repository.*;
import com.resolveiq.backend.service.CustomerAuthService;
import com.resolveiq.common.security.JwtTokenUtil;
import com.resolveiq.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rigorous End-to-End Security & Verification Test Suite for the ResolveIQ
 * Human Authentication and Customer Access Model (PRD §§11, 12, 35, 38).
 *
 * Verifies:
 * 1. Customer registration lifecycle & SHA-256 verification token hashing
 * 2. Strict invariant: Email verification does NOT grant dashboard access or JWT
 * 3. Server-controlled tenant/role assignment via Admin review workflow
 * 4. Passwordless customer login with single-use 6-digit OTP (cooldown, lockout, rate limit)
 * 5. Backwards-compatible internal operator authentication
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CustomerAccessModelAndOtpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CustomerRegistrationRepository customerRegistrationRepository;
    @Autowired private AuthOtpRepository authOtpRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenUtil jwtTokenUtil;

    private OrganizationEntity customerTenant;
    private OrganizationEntity systemOrg;
    private UserEntity adminUser;
    private UserEntity viewerUser;
    private String adminJwt;
    private String viewerJwt;

    @BeforeEach
    void setUp() {
        authOtpRepository.deleteAll();
        customerRegistrationRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        systemOrg = organizationRepository.save(new OrganizationEntity("ResolveIQ Internal " + suffix, "internal-" + suffix, "ENTERPRISE"));
        customerTenant = organizationRepository.save(new OrganizationEntity("Stripe Payments " + suffix, "stripe-" + suffix, "ENTERPRISE"));

        adminUser = userRepository.save(new UserEntity(systemOrg.getId(), "admin-" + suffix + "@resolveiq.io", "System Administrator", Role.ADMIN, null));
        viewerUser = userRepository.save(new UserEntity(systemOrg.getId(), "viewer-" + suffix + "@resolveiq.io", "Read-Only Operator", Role.VIEWER, null));

        adminJwt = jwtTokenUtil.generateAccessToken(systemOrg.getId(), adminUser.getId(), adminUser.getEmail(), adminUser.getRole());
        viewerJwt = jwtTokenUtil.generateAccessToken(systemOrg.getId(), viewerUser.getId(), viewerUser.getEmail(), viewerUser.getRole());
    }

    @Test
    @DisplayName("1. Customer registration creates record with PENDING_EMAIL_VERIFICATION status")
    void test1_CustomerRegistration_CreatesPendingEmailVerification() throws Exception {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "sarah@stripe.com",
                "Sarah Connor",
                "Stripe Inc",
                "Staff SRE"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sarah@stripe.com"))
                .andExpect(jsonPath("$.status").value("PENDING_EMAIL_VERIFICATION"))
                .andExpect(jsonPath("$.verificationToken").isString())
                .andReturn();

        CustomerRegistrationResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CustomerRegistrationResponse.class);

        assertThat(response.verificationToken()).isNotBlank();
        assertThat(response.verificationToken().length()).isEqualTo(64);

        CustomerRegistrationEntity inDb = customerRegistrationRepository.findByEmailIgnoreCase("sarah@stripe.com").orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo(RegistrationStatus.PENDING_EMAIL_VERIFICATION);
    }

    @Test
    @DisplayName("2. Raw email verification token is NOT stored in database; SHA-256 hash is stored")
    void test2_VerificationToken_HashedWithSha256InDb() throws Exception {
        CustomerRegistrationRequest request = new CustomerRegistrationRequest(
                "alex@datadog.com",
                "Alex Rivera",
                "Datadog",
                "Principal Architect"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        CustomerRegistrationResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), CustomerRegistrationResponse.class);

        String rawToken = response.verificationToken();
        CustomerRegistrationEntity inDb = customerRegistrationRepository.findByEmailIgnoreCase("alex@datadog.com").orElseThrow();

        assertThat(inDb.getVerificationTokenHash()).isNotEqualTo(rawToken);
        assertThat(inDb.getVerificationTokenHash()).isEqualTo(CustomerAuthService.hashSha256(rawToken));
    }

    @Test
    @DisplayName("3. Customer cannot request OTP or sign in while status is PENDING_EMAIL_VERIFICATION")
    void test3_CannotRequestOtpWhilePendingEmailVerification() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "dave@uber.com", "Dave Vance", "Uber", "SRE", "dummyhash", Instant.now().plus(24, ChronoUnit.HOURS)
        );
        customerRegistrationRepository.save(reg);

        OtpRequest otpReq = new OtpRequest("dave@uber.com");
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("verify your email address")));
    }

    @Test
    @DisplayName("4. Email verification advances status to PENDING_ADMIN_REVIEW")
    void test4_VerifyEmail_AdvancesToPendingAdminReview() throws Exception {
        CustomerRegistrationRequest req = new CustomerRegistrationRequest("bob@netflix.com", "Bob S", "Netflix", "Engineer");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        CustomerRegistrationResponse regDto = objectMapper.readValue(regRes.getResponse().getContentAsString(), CustomerRegistrationResponse.class);

        VerifyEmailRequest verifyReq = new VerifyEmailRequest(regDto.verificationToken());
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_ADMIN_REVIEW"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pending review")));

        CustomerRegistrationEntity inDb = customerRegistrationRepository.findByEmailIgnoreCase("bob@netflix.com").orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo(RegistrationStatus.PENDING_ADMIN_REVIEW);
        assertThat(inDb.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("5. Verification token is cleared (single-use) after verification")
    void test5_VerificationToken_SingleUse() throws Exception {
        CustomerRegistrationRequest req = new CustomerRegistrationRequest("carol@meta.com", "Carol D", "Meta", "SRE");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        CustomerRegistrationResponse regDto = objectMapper.readValue(regRes.getResponse().getContentAsString(), CustomerRegistrationResponse.class);

        // First verification succeeds
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(regDto.verificationToken()))))
                .andExpect(status().isOk());

        // Reusing token fails because verification_token_hash was cleared
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(regDto.verificationToken()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. CRITICAL SECURITY INVARIANT: Email verification does NOT grant dashboard access or JWT")
    void test6_EmailVerification_DoesNotIssueJwtOrGrantAccess() throws Exception {
        CustomerRegistrationRequest req = new CustomerRegistrationRequest("eve@apple.com", "Eve Hacker", "Apple", "Security");
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        CustomerRegistrationResponse regDto = objectMapper.readValue(regRes.getResponse().getContentAsString(), CustomerRegistrationResponse.class);

        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(regDto.verificationToken()))))
                .andExpect(status().isOk())
                .andReturn();

        // Ensure NO accessToken field in response
        JsonNode json = objectMapper.readTree(verifyRes.getResponse().getContentAsString());
        assertThat(json.has("accessToken")).isFalse();

        // Customer cannot access protected API endpoints without a valid JWT
        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("7. Customer cannot request OTP or sign in while status is PENDING_ADMIN_REVIEW")
    void test7_CannotRequestOtpWhilePendingAdminReview() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "george@airbnb.com", "George K", "Airbnb", "SRE", null, null
        );
        reg.setStatus(RegistrationStatus.PENDING_ADMIN_REVIEW);
        reg.setEmailVerifiedAt(Instant.now());
        customerRegistrationRepository.save(reg);

        OtpRequest otpReq = new OtpRequest("george@airbnb.com");
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("awaiting administrator review")));
    }

    @Test
    @DisplayName("8. Duplicate registration when already in PENDING_ADMIN_REVIEW is rejected")
    void test8_DuplicateRegistration_Rejected() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "helen@slack.com", "Helen M", "Slack", "Manager", null, null
        );
        reg.setStatus(RegistrationStatus.PENDING_ADMIN_REVIEW);
        customerRegistrationRepository.save(reg);

        CustomerRegistrationRequest req = new CustomerRegistrationRequest("helen@slack.com", "Helen M", "Slack", "Manager");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("currently pending administrator review")));
    }

    @Test
    @DisplayName("9. Admin registration review endpoint is FORBIDDEN to non-admin roles (RBAC)")
    void test9_AdminRegistrations_ForbiddenToNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/registrations")
                        .header("Authorization", "Bearer " + viewerJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("10. Admin registration review endpoint allows ADMIN to list registrations")
    void test10_AdminRegistrations_AllowedForAdmin() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "ian@google.com", "Ian G", "Google", "SWE", null, null
        );
        reg.setStatus(RegistrationStatus.PENDING_ADMIN_REVIEW);
        customerRegistrationRepository.save(reg);

        mockMvc.perform(get("/api/v1/admin/registrations")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].email").value("ian@google.com"));
    }

    @Test
    @DisplayName("11. Admin approves registration and assigns organization and role")
    void test11_AdminApprove_SetsAssignedTenantAndRole() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "jerry@stripe.com", "Jerry Seinfeld", "Stripe", "SRE", null, null
        );
        reg.setStatus(RegistrationStatus.PENDING_ADMIN_REVIEW);
        reg = customerRegistrationRepository.save(reg);

        AdminApproveRequest approveReq = new AdminApproveRequest(customerTenant.getId(), Role.SRE);

        mockMvc.perform(post("/api/v1/admin/registrations/" + reg.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.assignedTenantId").value(customerTenant.getId().toString()))
                .andExpect(jsonPath("$.data.assignedRole").value("SRE"));

        CustomerRegistrationEntity inDb = customerRegistrationRepository.findById(reg.getId()).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo(RegistrationStatus.ACTIVE);
        assertThat(inDb.getAssignedTenantId()).isEqualTo(customerTenant.getId());
        assertThat(inDb.getAssignedRole()).isEqualTo(Role.SRE);
    }

    @Test
    @DisplayName("12. Admin approval provisions/activates UserEntity in target tenant")
    void test12_AdminApprove_ProvisionsUserInTargetTenant() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "kramer@stripe.com", "Cosmo Kramer", "Stripe", "DevOps", null, null
        );
        reg.setStatus(RegistrationStatus.PENDING_ADMIN_REVIEW);
        reg = customerRegistrationRepository.save(reg);

        AdminApproveRequest approveReq = new AdminApproveRequest(customerTenant.getId(), Role.DEVELOPER);

        mockMvc.perform(post("/api/v1/admin/registrations/" + reg.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk());

        UserEntity user = userRepository.findByTenantIdAndEmail(customerTenant.getId(), "kramer@stripe.com").orElseThrow();
        assertThat(user.getFullName()).isEqualTo("Cosmo Kramer");
        assertThat(user.getRole()).isEqualTo(Role.DEVELOPER);
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("13. Admin can reject registration with a reason")
    void test13_AdminReject_SetsStatusRejectedWithReason() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "malicious@sketchy.com", "Mal Actor", "Sketchy LLC", "Hacker", null, null
        );
        reg.setStatus(RegistrationStatus.PENDING_ADMIN_REVIEW);
        reg = customerRegistrationRepository.save(reg);

        AdminRejectRequest rejectReq = new AdminRejectRequest("Corporate email required; sketchy domains not allowed.");

        mockMvc.perform(post("/api/v1/admin/registrations/" + reg.getId() + "/reject")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Corporate email required; sketchy domains not allowed."));

        CustomerRegistrationEntity inDb = customerRegistrationRepository.findById(reg.getId()).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
    }

    @Test
    @DisplayName("14. Rejected customer cannot request OTP or sign in")
    void test14_RejectedCustomer_CannotRequestOtp() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "denied@corp.com", "Denied User", "Corp", "Eng", null, null
        );
        reg.setStatus(RegistrationStatus.REJECTED);
        reg.setRejectionReason("Domain blocked");
        customerRegistrationRepository.save(reg);

        OtpRequest otpReq = new OtpRequest("denied@corp.com");
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Account registration was rejected")));
    }

    @Test
    @DisplayName("15. Approved/Active customer requests OTP: 6-digit numeric code hashed with SHA-256 in DB")
    void test15_ApprovedCustomer_RequestOtp_SixDigitNumericHashed() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "elaine@stripe.com", "Elaine Benes", "Stripe", "VP Eng", null, null
        );
        reg.setStatus(RegistrationStatus.ACTIVE);
        reg.setAssignedTenantId(customerTenant.getId());
        reg.setAssignedRole(Role.SRE);
        customerRegistrationRepository.save(reg);

        MvcResult res = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest("elaine@stripe.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andExpect(jsonPath("$.devOtp").isString())
                .andReturn();

        OtpResponse otpResp = objectMapper.readValue(res.getResponse().getContentAsString(), OtpResponse.class);
        assertThat(otpResp.devOtp()).matches("^\\d{6}$");

        AuthOtpEntity inDb = authOtpRepository.findTopByEmailIgnoreCaseAndConsumedFalseOrderByCreatedAtDesc("elaine@stripe.com").orElseThrow();
        assertThat(inDb.getOtpHash()).isNotEqualTo(otpResp.devOtp());
        assertThat(inDb.getOtpHash()).isEqualTo(CustomerAuthService.hashSha256(otpResp.devOtp()));
        assertThat(inDb.getExpiresAt()).isAfter(Instant.now().plus(4, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("16. OTP request enforces 60-second cooldown between requests")
    void test16_OtpRequest_EnforcesCooldown() throws Exception {
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                "fast@stripe.com", "Fast Requester", "Stripe", "SRE", null, null
        );
        reg.setStatus(RegistrationStatus.ACTIVE);
        reg.setAssignedTenantId(customerTenant.getId());
        reg.setAssignedRole(Role.SRE);
        customerRegistrationRepository.save(reg);

        // First OTP request succeeds
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest("fast@stripe.com"))))
                .andExpect(status().isOk());

        // Immediate subsequent request fails with cooldown error
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest("fast@stripe.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("wait")));
    }

    @Test
    @DisplayName("17. OTP request enforces rate limit of maximum 5 requests per 15 minutes")
    void test17_OtpRequest_EnforcesRateLimit() throws Exception {
        String email = "ratelimit@stripe.com";
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                email, "Rate Limit", "Stripe", "SRE", null, null
        );
        reg.setStatus(RegistrationStatus.ACTIVE);
        reg.setAssignedTenantId(customerTenant.getId());
        reg.setAssignedRole(Role.SRE);
        customerRegistrationRepository.save(reg);

        // Insert 5 past OTPs in the last 15 minutes
        for (int i = 0; i < 5; i++) {
            AuthOtpEntity otp = new AuthOtpEntity(email, "hash" + i, Instant.now().plus(5, ChronoUnit.MINUTES));
            otp.setCreatedAt(Instant.now().minus(70 + (i * 10), ChronoUnit.SECONDS)); // satisfy 60s cooldown
            authOtpRepository.save(otp);
        }

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(email))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("Maximum 5 requests per 15 minutes")));
    }

    @Test
    @DisplayName("18. OTP verification with invalid code increments attempts and reports remaining count")
    void test18_VerifyOtp_InvalidCodeIncrementsAttempts() throws Exception {
        String email = "verifytest@stripe.com";
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                email, "Verify User", "Stripe", "SRE", null, null
        );
        reg.setStatus(RegistrationStatus.ACTIVE);
        reg.setAssignedTenantId(customerTenant.getId());
        reg.setAssignedRole(Role.SRE);
        customerRegistrationRepository.save(reg);

        // Request OTP
        MvcResult res = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(email))))
                .andExpect(status().isOk())
                .andReturn();

        OtpVerifyRequest wrongVerify = new OtpVerifyRequest(email, "000000");
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongVerify)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("4 attempts remaining")));

        AuthOtpEntity inDb = authOtpRepository.findTopByEmailIgnoreCaseAndConsumedFalseOrderByCreatedAtDesc(email).orElseThrow();
        assertThat(inDb.getAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("19. OTP verification locks out and invalidates after 5 failed attempts")
    void test19_VerifyOtp_LocksOutAfterFiveAttempts() throws Exception {
        String email = "lockout@stripe.com";
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                email, "Lockout User", "Stripe", "SRE", null, null
        );
        reg.setStatus(RegistrationStatus.ACTIVE);
        reg.setAssignedTenantId(customerTenant.getId());
        reg.setAssignedRole(Role.SRE);
        customerRegistrationRepository.save(reg);

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(email))))
                .andExpect(status().isOk());

        // Perform 5 invalid attempts
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/otp/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new OtpVerifyRequest(email, "00000" + i))))
                    .andExpect(status().isBadRequest());
        }

        // 6th attempt fails with lockout message
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(email, "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("No valid OTP found")));
    }

    @Test
    @DisplayName("20. Expired OTP is rejected upon verification")
    void test20_VerifyOtp_ExpiredOtpRejected() throws Exception {
        String email = "expired@stripe.com";
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                email, "Expired User", "Stripe", "SRE", null, null
        );
        reg.setStatus(RegistrationStatus.ACTIVE);
        reg.setAssignedTenantId(customerTenant.getId());
        reg.setAssignedRole(Role.SRE);
        customerRegistrationRepository.save(reg);

        AuthOtpEntity expiredOtp = new AuthOtpEntity(email, CustomerAuthService.hashSha256("123456"), Instant.now().minus(1, ChronoUnit.MINUTES));
        authOtpRepository.save(expiredOtp);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(email, "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    @DisplayName("21. Valid OTP verification issues JWT with server-assigned tenant & role and activates account")
    void test21_VerifyOtp_ValidCodeIssuesJwtWithCorrectTenantAndRole() throws Exception {
        String email = "george.costanza@stripe.com";
        CustomerRegistrationEntity reg = new CustomerRegistrationEntity(
                email, "George Costanza", "Stripe", "Architect", null, null
        );
        reg.setStatus(RegistrationStatus.APPROVED);
        reg.setAssignedTenantId(customerTenant.getId());
        reg.setAssignedRole(Role.INCIDENT_MANAGER);
        customerRegistrationRepository.save(reg);

        // Request OTP
        MvcResult reqRes = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpRequest(email))))
                .andExpect(status().isOk())
                .andReturn();

        OtpResponse otpResp = objectMapper.readValue(reqRes.getResponse().getContentAsString(), OtpResponse.class);

        // Verify with valid OTP
        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OtpVerifyRequest(email, otpResp.devOtp()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.tenantId").value(customerTenant.getId().toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("INCIDENT_MANAGER"))
                .andReturn();

        AuthController.AuthResponse authResp = objectMapper.readValue(
                verifyRes.getResponse().getContentAsString(), AuthController.AuthResponse.class);

        // Validate cryptographic JWT claims
        JwtTokenUtil.TokenClaims claims = jwtTokenUtil.parseAndValidateToken(authResp.accessToken());
        assertThat(claims.tenantId()).isEqualTo(customerTenant.getId());
        assertThat(claims.email()).isEqualTo(email);
        assertThat(claims.role()).isEqualTo(Role.INCIDENT_MANAGER);

        // Ensure registration status transitioned to ACTIVE
        CustomerRegistrationEntity inDb = customerRegistrationRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(inDb.getStatus()).isEqualTo(RegistrationStatus.ACTIVE);
    }

    @Test
    @DisplayName("22. Internal user login (/api/v1/auth/login) remains fully functional")
    void test22_InternalUserLogin_RemainsFunctional() throws Exception {
        AuthController.LoginRequest loginReq = new AuthController.LoginRequest(
                "admin@resolveiq.io",
                "irrelevant_internal_secret",
                systemOrg.getId(),
                Role.ADMIN
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tenantId").value(systemOrg.getId().toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
