package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.KnowledgeDocEntity;
import com.resolveiq.backend.rag.service.KnowledgeIngestionService;
import com.resolveiq.backend.repository.KnowledgeDocRepository;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Dedicated REST API for Runbooks per PRD §22, §35.
 */
@RestController
@RequestMapping("/api/v1/runbooks")
public class RunbookController {

    private final KnowledgeDocRepository docRepository;
    private final KnowledgeIngestionService ingestionService;
    private final AuditLogService auditLogService;

    public RunbookController(
            KnowledgeDocRepository docRepository,
            KnowledgeIngestionService ingestionService,
            AuditLogService auditLogService) {
        this.docRepository = docRepository;
        this.ingestionService = ingestionService;
        this.auditLogService = auditLogService;
    }

    public record CreateRunbookRequest(
            @NotBlank(message = "Title cannot be blank") String title,
            String sourceUri,
            String service,
            String environment,
            @NotBlank(message = "Content cannot be blank") String content,
            String metadataJson,
            UUID projectId
    ) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE')")
    public ResponseEntity<ApiResponse<KnowledgeDocEntity>> createRunbook(@Valid @RequestBody CreateRunbookRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        KnowledgeDocEntity doc = ingestionService.ingestDocument(
                tenantId,
                request.projectId(),
                KnowledgeDocType.RUNBOOK,
                request.title(),
                request.sourceUri(),
                request.service(),
                request.environment(),
                request.content(),
                request.metadataJson()
        );

        auditLogService.recordCurrentContext(
                "RUNBOOK_CREATED",
                "runbooks:" + doc.getId(),
                null,
                "title=" + doc.getTitle() + ", service=" + doc.getService()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ofData(doc));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<KnowledgeDocEntity>> listRunbooks(
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<KnowledgeDocEntity> results;
        if (service != null && !service.isBlank()) {
            results = docRepository.findByTenantIdAndDocTypeAndService(tenantId, KnowledgeDocType.RUNBOOK, service, pageable);
        } else {
            results = docRepository.findByTenantIdAndDocType(tenantId, KnowledgeDocType.RUNBOOK, pageable);
        }

        return ResponseEntity.ok(ApiResponse.ofItems(results.getContent(), null, results.getTotalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<KnowledgeDocEntity>> getRunbook(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        KnowledgeDocEntity doc = docRepository.findByIdAndTenantId(id, tenantId)
                .filter(d -> d.getDocType() == KnowledgeDocType.RUNBOOK)
                .orElseThrow(() -> new ResourceNotFoundException("Runbook not found: " + id));
        return ResponseEntity.ok(ApiResponse.ofData(doc));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE')")
    public ResponseEntity<Void> deleteRunbook(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        KnowledgeDocEntity doc = docRepository.findByIdAndTenantId(id, tenantId)
                .filter(d -> d.getDocType() == KnowledgeDocType.RUNBOOK)
                .orElseThrow(() -> new ResourceNotFoundException("Runbook not found: " + id));

        ingestionService.deleteDocument(tenantId, id);

        auditLogService.recordCurrentContext(
                "RUNBOOK_DELETED",
                "runbooks:" + id,
                "active",
                "deleted"
        );

        return ResponseEntity.noContent().build();
    }
}
