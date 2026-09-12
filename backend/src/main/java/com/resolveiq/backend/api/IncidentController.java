package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.backend.domain.IncidentEventEntity;
import com.resolveiq.backend.domain.RootCauseCandidateEntity;
import com.resolveiq.backend.service.IncidentService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import com.resolveiq.common.incident.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Incidents according to PRD §19, §35.3.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    public record StatusUpdateRequest(
            @NotNull(message = "Status cannot be null") IncidentStatus status,
            String notes
    ) {}

    public record SeverityUpdateRequest(
            @NotNull(message = "Severity cannot be null") IncidentSeverity severity,
            @NotBlank(message = "Reason cannot be blank") String reason
    ) {}

    public record AssignmentUpdateRequest(
            @NotNull(message = "Assignee ID cannot be null") UUID assigneeId
    ) {}

    public record CommentRequest(
            @NotBlank(message = "Comment cannot be blank") String comment
    ) {}

    public record CandidateVerificationRequest(
            @NotNull(message = "Verification status cannot be null") VerificationStatus status,
            String notes,
            Integer rating
    ) {}

    @GetMapping
    public ResponseEntity<ApiResponse<IncidentEntity>> listIncidents(
            @RequestParam(value = "status", required = false) IncidentStatus status,
            @RequestParam(value = "severity", required = false) IncidentSeverity severity,
            @RequestParam(value = "service", required = false) String service) {
        List<IncidentEntity> items = incidentService.listIncidents(status, severity, service);
        return ResponseEntity.ok(ApiResponse.ofItems(items, null, (long) items.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentEntity> getIncident(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(incidentService.getIncident(id));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE')")
    public ResponseEntity<IncidentEntity> updateIncidentStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody StatusUpdateRequest request) {
        IncidentEntity updated = incidentService.updateStatus(id, request.status(), request.notes());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/severity")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE')")
    public ResponseEntity<IncidentEntity> updateIncidentSeverity(
            @PathVariable("id") UUID id,
            @Valid @RequestBody SeverityUpdateRequest request) {
        IncidentEntity updated = incidentService.updateSeverity(id, request.severity(), request.reason());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE')")
    public ResponseEntity<IncidentEntity> assignIncident(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AssignmentUpdateRequest request) {
        IncidentEntity updated = incidentService.assignIncident(id, request.assigneeId());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponse<IncidentEventEntity>> getIncidentTimeline(@PathVariable("id") UUID id) {
        List<IncidentEventEntity> events = incidentService.getTimeline(id);
        return ResponseEntity.ok(ApiResponse.ofItems(events, null, (long) events.size()));
    }

    @PostMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE', 'DEVELOPER')")
    public ResponseEntity<IncidentEventEntity> addComment(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CommentRequest request) {
        IncidentEventEntity event = incidentService.addComment(id, request.comment());
        return ResponseEntity.ok(event);
    }

    @GetMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<RootCauseCandidateEntity>> getCandidates(@PathVariable("id") UUID id) {
        List<RootCauseCandidateEntity> candidates = incidentService.getCandidates(id);
        return ResponseEntity.ok(ApiResponse.ofItems(candidates, null, (long) candidates.size()));
    }

    @PostMapping("/{id}/candidates/{candidateId}/verify")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE')")
    public ResponseEntity<RootCauseCandidateEntity> verifyCandidate(
            @PathVariable("id") UUID id,
            @PathVariable("candidateId") UUID candidateId,
            @Valid @RequestBody CandidateVerificationRequest request) {
        RootCauseCandidateEntity updated = incidentService.verifyCandidate(
                id, candidateId, request.status(), request.notes(), request.rating()
        );
        return ResponseEntity.ok(updated);
    }
}
