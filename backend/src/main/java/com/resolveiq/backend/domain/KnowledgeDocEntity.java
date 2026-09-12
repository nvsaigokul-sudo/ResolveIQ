package com.resolveiq.backend.domain;

import com.resolveiq.common.knowledge.KnowledgeDocType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted knowledge document entity (postmortems, runbooks, architecture docs) per PRD §22, §25, §36.1.
 */
@Entity
@Table(name = "knowledge_docs")
public class KnowledgeDocEntity extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id")
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private KnowledgeDocType docType;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_uri")
    private String sourceUri;

    private String service;

    private String environment;

    @Column(name = "raw_content", nullable = false, columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public KnowledgeDocEntity() {
    }

    public KnowledgeDocEntity(UUID tenantId, UUID projectId, KnowledgeDocType docType, String title,
                              String sourceUri, String service, String environment,
                              String rawContent, String contentHash, String metadata) {
        super(tenantId);
        this.projectId = projectId;
        this.docType = docType;
        this.title = title;
        this.sourceUri = sourceUri;
        this.service = service;
        this.environment = environment;
        this.rawContent = rawContent;
        this.contentHash = contentHash;
        this.metadata = metadata;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public KnowledgeDocType getDocType() {
        return docType;
    }

    public void setDocType(KnowledgeDocType docType) {
        this.docType = docType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
