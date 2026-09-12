package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.AuditLogEntity;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AuditLogEntity>> listAuditLogs(@RequestParam(name = "action", required = false) String action) {
        List<AuditLogEntity> logs;
        if (action != null && !action.isBlank()) {
            logs = auditLogService.getAuditLogsByAction(action);
        } else {
            logs = auditLogService.getAuditLogsForCurrentTenant();
        }
        return ResponseEntity.ok(ApiResponse.ofItems(logs, null, (long) logs.size()));
    }
}
