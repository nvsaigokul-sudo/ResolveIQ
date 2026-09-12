package com.resolveiq.backend.api;

import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final AuditLogService auditLogService;

    public IncidentController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public record StatusUpdateRequest(
            @NotBlank(message = "Status cannot be blank") String status
    ) {}

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listIncidents() {
        return ResponseEntity.ok(ApiResponse.ofItems(Collections.emptyList(), null, 0L));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE')")
    public ResponseEntity<Map<String, Object>> updateIncidentStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody StatusUpdateRequest request) {

        auditLogService.recordCurrentContext("INCIDENT_STATUS_CHANGED", "incident:" + id, null, "status:" + request.status());
        return ResponseEntity.ok(Map.of(
                "id", id,
                "status", request.status()
        ));
    }
}
