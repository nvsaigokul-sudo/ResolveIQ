package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.EvidenceEntity;
import com.resolveiq.backend.service.EvidenceBuilderService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.incident.EvidenceSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Evidence retrieval and attachment according to PRD §21, §35.3.
 */
@RestController
@RequestMapping("/api/v1/incidents/{incidentId}/evidence")
public class EvidenceController {

    private final EvidenceBuilderService evidenceBuilderService;
    private final com.resolveiq.backend.service.IncidentService incidentService;

    public EvidenceController(EvidenceBuilderService evidenceBuilderService,
                              com.resolveiq.backend.service.IncidentService incidentService) {
        this.evidenceBuilderService = evidenceBuilderService;
        this.incidentService = incidentService;
    }

    public record ManualEvidenceRequest(
            @NotNull(message = "Source cannot be null") EvidenceSource source,
            @NotBlank(message = "Service cannot be blank") String service,
            String queryUsed,
            String resultReference,
            @NotBlank(message = "Content cannot be blank") String content,
            Double relevanceScore,
            Double confidence,
            String relationshipToHypothesis
    ) {}

    @GetMapping
    public ResponseEntity<ApiResponse<EvidenceEntity>> getEvidence(
            @PathVariable("incidentId") UUID incidentId,
            @RequestParam(value = "source", required = false) EvidenceSource source) {
        // Verify incident exists and belongs to current tenant (returns 404 per PRD §35.3 if not found)
        incidentService.getIncident(incidentId);

        List<EvidenceEntity> items = source != null
                ? evidenceBuilderService.getEvidenceBySource(incidentId, source)
                : evidenceBuilderService.getEvidenceForIncident(incidentId);

        return ResponseEntity.ok(ApiResponse.ofItems(items, null, (long) items.size()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE', 'DEVELOPER')")
    public ResponseEntity<EvidenceEntity> attachEvidence(
            @PathVariable("incidentId") UUID incidentId,
            @Valid @RequestBody ManualEvidenceRequest request) {
        incidentService.getIncident(incidentId);
        EvidenceEntity entity = evidenceBuilderService.recordManualEvidence(
                incidentId,
                request.source(),
                request.service(),
                request.queryUsed(),
                request.resultReference(),
                request.content(),
                request.relevanceScore(),
                request.confidence(),
                request.relationshipToHypothesis()
        );
        return ResponseEntity.ok(entity);
    }
}
