package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.RegistrationStatus;
import com.resolveiq.backend.dto.auth.AdminApproveRequest;
import com.resolveiq.backend.dto.auth.AdminRejectRequest;
import com.resolveiq.backend.dto.auth.CustomerRegistrationDto;
import com.resolveiq.backend.service.AdminRegistrationService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller for internal administrator review and approval of customer registrations.
 * Strictly gated to ADMIN and OWNER roles via @PreAuthorize (PRD Sections 11, 12, 38).
 */
@RestController
@RequestMapping("/api/v1/admin/registrations")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
public class AdminRegistrationController {

    private final AdminRegistrationService adminRegistrationService;

    public AdminRegistrationController(AdminRegistrationService adminRegistrationService) {
        this.adminRegistrationService = adminRegistrationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CustomerRegistrationDto>> listRegistrations(
            @RequestParam(required = false) RegistrationStatus status) {
        List<CustomerRegistrationDto> items = adminRegistrationService.listRegistrations(status);
        return ResponseEntity.ok(ApiResponse.ofItems(items, null, (long) items.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerRegistrationDto>> getRegistration(@PathVariable UUID id) {
        CustomerRegistrationDto item = adminRegistrationService.getRegistration(id);
        return ResponseEntity.ok(ApiResponse.ofData(item));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<CustomerRegistrationDto>> approve(
            @PathVariable UUID id,
            @Valid @RequestBody AdminApproveRequest request) {
        UUID adminUserId = TenantContextHolder.getContext()
                .map(TenantContext::userId)
                .orElse(null);

        CustomerRegistrationDto item = adminRegistrationService.approveRegistration(id, request, adminUserId);
        return ResponseEntity.ok(ApiResponse.ofData(item));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<CustomerRegistrationDto>> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) AdminRejectRequest request) {
        UUID adminUserId = TenantContextHolder.getContext()
                .map(TenantContext::userId)
                .orElse(null);

        CustomerRegistrationDto item = adminRegistrationService.rejectRegistration(id, request, adminUserId);
        return ResponseEntity.ok(ApiResponse.ofData(item));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<CustomerRegistrationDto>> suspend(@PathVariable UUID id) {
        UUID adminUserId = TenantContextHolder.getContext()
                .map(TenantContext::userId)
                .orElse(null);

        CustomerRegistrationDto item = adminRegistrationService.suspendRegistration(id, adminUserId);
        return ResponseEntity.ok(ApiResponse.ofData(item));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<CustomerRegistrationDto>> activate(@PathVariable UUID id) {
        UUID adminUserId = TenantContextHolder.getContext()
                .map(TenantContext::userId)
                .orElse(null);

        CustomerRegistrationDto item = adminRegistrationService.activateRegistration(id, adminUserId);
        return ResponseEntity.ok(ApiResponse.ofData(item));
    }
}
