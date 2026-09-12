package com.resolveiq.backend.api;

import com.resolveiq.backend.domain.KnowledgeDocEntity;
import com.resolveiq.backend.rag.retrieval.HybridRetrievalEngine;
import com.resolveiq.backend.rag.service.KnowledgeIngestionService;
import com.resolveiq.backend.repository.KnowledgeDocRepository;
import com.resolveiq.backend.service.AuditLogService;
import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import com.resolveiq.common.knowledge.KnowledgeSearchRequestDto;
import com.resolveiq.common.knowledge.KnowledgeSearchResultDto;
import com.resolveiq.common.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for Historical Knowledge and Runbooks per PRD §22, §25, §35.3.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {

    private final KnowledgeDocRepository docRepository;
    private final KnowledgeIngestionService ingestionService;
    private final HybridRetrievalEngine retrievalEngine;
    private final AuditLogService auditLogService;

    public KnowledgeController(
            KnowledgeDocRepository docRepository,
            KnowledgeIngestionService ingestionService,
            HybridRetrievalEngine retrievalEngine,
            AuditLogService auditLogService) {
        this.docRepository = docRepository;
        this.ingestionService = ingestionService;
        this.retrievalEngine = retrievalEngine;
        this.auditLogService = auditLogService;
    }

    public record IngestKnowledgeRequest(
            @NotNull(message = "docType cannot be null") KnowledgeDocType docType,
            @NotBlank(message = "title cannot be blank") String title,
            String sourceUri,
            String service,
            String environment,
            @NotBlank(message = "content cannot be blank") String content,
            String metadataJson,
            UUID projectId
    ) {}

    /**
     * Ingest a new knowledge document (SRE, Incident Manager, Admin, Owner).
     */
    @PostMapping("/docs")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE')")
    public ResponseEntity<ApiResponse<KnowledgeDocEntity>> ingestDocument(@Valid @RequestBody IngestKnowledgeRequest request) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        KnowledgeDocEntity doc = ingestionService.ingestDocument(
                tenantId,
                request.projectId(),
                request.docType(),
                request.title(),
                request.sourceUri(),
                request.service(),
                request.environment(),
                request.content(),
                request.metadataJson()
        );

        auditLogService.recordCurrentContext(
                "KNOWLEDGE_INGESTED",
                "knowledge_docs:" + doc.getId(),
                null,
                "type=" + doc.getDocType() + ", title=" + doc.getTitle()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ofData(doc));
    }

    /**
     * List knowledge documents for current tenant.
     */
    @GetMapping("/docs")
    public ResponseEntity<ApiResponse<KnowledgeDocEntity>> listDocuments(
            @RequestParam(value = "docType", required = false) KnowledgeDocType docType,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<KnowledgeDocEntity> results;
        if (docType != null && service != null && !service.isBlank()) {
            results = docRepository.findByTenantIdAndDocTypeAndService(tenantId, docType, service, pageable);
        } else if (docType != null) {
            results = docRepository.findByTenantIdAndDocType(tenantId, docType, pageable);
        } else if (service != null && !service.isBlank()) {
            results = docRepository.findByTenantIdAndService(tenantId, service, pageable);
        } else {
            results = docRepository.findByTenantId(tenantId, pageable);
        }

        return ResponseEntity.ok(ApiResponse.ofItems(results.getContent(), null, results.getTotalElements()));
    }

    /**
     * Get single knowledge document by ID. Returns 404 on cross-tenant access.
     */
    @GetMapping("/docs/{id}")
    public ResponseEntity<ApiResponse<KnowledgeDocEntity>> getDocument(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        KnowledgeDocEntity doc = docRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge document not found: " + id));
        return ResponseEntity.ok(ApiResponse.ofData(doc));
    }

    /**
     * Delete knowledge document. Returns 404 on cross-tenant access.
     */
    @DeleteMapping("/docs/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'INCIDENT_MANAGER', 'SRE')")
    public ResponseEntity<Void> deleteDocument(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        boolean deleted = ingestionService.deleteDocument(tenantId, id);
        if (!deleted) {
            throw new ResourceNotFoundException("Knowledge document not found: " + id);
        }

        auditLogService.recordCurrentContext(
                "KNOWLEDGE_DELETED",
                "knowledge_docs:" + id,
                "active",
                "deleted"
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * Hybrid Search (BM25 + Dense Vector Similarity).
     */
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<KnowledgeSearchResultDto>> searchKnowledge(
            @Valid @RequestBody KnowledgeSearchRequestDto request) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenantId, request);
        return ResponseEntity.ok(ApiResponse.ofItems(results, null, (long) results.size()));
    }
}
