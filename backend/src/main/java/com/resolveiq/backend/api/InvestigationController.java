package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.InvestigationEntity;
import com.resolveiq.backend.domain.RootCauseCandidateEntity;
import com.resolveiq.backend.investigation.dto.InvestigationResultDto;
import com.resolveiq.backend.investigation.service.InvestigationService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ForbiddenException;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.security.Role;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class InvestigationController {

    private final InvestigationService investigationService;

    public InvestigationController(InvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    @PostMapping("/{id}/investigate")
    public ResponseEntity<ApiResponse<InvestigationResultDto>> triggerInvestigation(
            @PathVariable("id") UUID incidentId,
            @RequestBody(required = false) Map<String, String> body) {
        requirePermission("trigger AI investigation");

        String triggerReason = body != null ? body.get("reason") : "Manual trigger via REST API";
        InvestigationResultDto result = investigationService.investigate(incidentId, triggerReason);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}/investigation")
    public ResponseEntity<ApiResponse<InvestigationEntity>> getLatestInvestigation(
            @PathVariable("id") UUID incidentId) {
        InvestigationEntity investigation = investigationService.getLatestInvestigation(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("No investigation found for incident: " + incidentId));
        return ResponseEntity.ok(ApiResponse.ok(investigation));
    }


    private void requirePermission(String action) {
        Role role = TenantContextHolder.getContext().map(TenantContext::role).orElse(Role.VIEWER);
        if (!role.canTriggerInvestigation()) {
            throw new ForbiddenException("Role " + role + " is not authorized to " + action);
        }
    }
}
