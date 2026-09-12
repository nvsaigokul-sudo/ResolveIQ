package com.resolveiq.backend.rag;

import com.resolveiq.backend.domain.KnowledgeChunkEntity;
import com.resolveiq.backend.domain.KnowledgeDocEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.rag.retrieval.HybridRetrievalEngine;
import com.resolveiq.backend.rag.service.KnowledgeIngestionService;
import com.resolveiq.backend.repository.KnowledgeChunkRepository;
import com.resolveiq.backend.repository.KnowledgeDocRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.exception.ResourceNotFoundException;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import com.resolveiq.common.knowledge.KnowledgeSearchRequestDto;
import com.resolveiq.common.knowledge.KnowledgeSearchResultDto;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationAdversarialTest {

    @Autowired
    private KnowledgeIngestionService ingestionService;

    @Autowired
    private HybridRetrievalEngine retrievalEngine;

    @Autowired
    private KnowledgeDocRepository docRepository;

    @Autowired
    private KnowledgeChunkRepository chunkRepository;

    @Autowired
    private TenantService tenantService;

    private OrganizationEntity tenantA;
    private OrganizationEntity tenantB;
    private KnowledgeDocEntity tenantBDoc;
    private KnowledgeDocEntity tenantBRunbook;

    @BeforeEach
    void setUp() {
        tenantA = tenantService.createOrganization("Tenant Alpha", "alpha-" + UUID.randomUUID().toString().substring(0, 8), "ENTERPRISE");
        tenantB = tenantService.createOrganization("Tenant Bravo", "bravo-" + UUID.randomUUID().toString().substring(0, 8), "ENTERPRISE");

        // Ingest confidential documents for Tenant B
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantB.getId(), "setup-bravo"));
        tenantBDoc = ingestionService.ingestDocument(
                tenantB.getId(), null, KnowledgeDocType.POSTMORTEM,
                "Bravo Confidential Architecture Postmortem", "https://bravo.internal/postmortems/db-incident",
                "bravo-auth-service", "prod",
                "# Bravo Confidential Outage\nSecret token BRAVO_SECRET_TOKEN_99182746 was exposed during database connection failover.",
                null
        );

        tenantBRunbook = ingestionService.ingestDocument(
                tenantB.getId(), null, KnowledgeDocType.RUNBOOK,
                "Bravo Emergency Runbook", "https://bravo.internal/runbooks/db-failover",
                "bravo-auth-service", "prod",
                "# Bravo Emergency Failover\nExecute immediate failover to bravo secondary cluster.",
                null
        );

        // Ingest normal document for Tenant A
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "setup-alpha"));
        ingestionService.ingestDocument(
                tenantA.getId(), null, KnowledgeDocType.POSTMORTEM,
                "Alpha Public Gateway Incident", "https://alpha.internal/postmortems/gw",
                "alpha-gateway", "prod",
                "# Alpha Gateway Latency Analysis\nGeneral gateway latency analysis for Tenant Alpha.",
                null
        );

        TenantContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Adversarial Search: Tenant A exact keyword search for Tenant B secret yields 0 results for Tenant B")
    void testTenantCannotRetrieveOtherTenantExactKeyword() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "alpha-adversarial-test"));

        KnowledgeSearchRequestDto request = new KnowledgeSearchRequestDto(
                "BRAVO_SECRET_TOKEN_99182746", null, null, 10
        );

        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenantA.getId(), request);
        // Tenant A must NEVER see any records or tokens belonging to Tenant B
        assertThat(results).noneMatch(r -> r.getTenantId().equals(tenantB.getId()));
        assertThat(results).noneMatch(r -> r.getChunkText().contains("BRAVO_SECRET_TOKEN_99182746"));
        assertThat(results).noneMatch(r -> r.getDocTitle().contains("Bravo"));

        // With minimum relevance score threshold, unrelated results are filtered out
        request.setMinScore(0.20);
        List<KnowledgeSearchResultDto> filteredResults = retrievalEngine.search(tenantA.getId(), request);
        assertThat(filteredResults).isEmpty();
    }

    @Test
    @DisplayName("Adversarial Search: Tenant A semantic vector search for Tenant B content yields 0 results")
    void testTenantCannotRetrieveOtherTenantSemanticVector() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "alpha-adversarial-test"));

        KnowledgeSearchRequestDto request = new KnowledgeSearchRequestDto(
                "Secret token was exposed during database connection failover", null, null, 10
        );
        request.setAlphaWeight(1.0); // 100% vector similarity

        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenantA.getId(), request);

        for (KnowledgeSearchResultDto result : results) {
            assertThat(result.getTenantId()).isEqualTo(tenantA.getId());
            assertThat(result.getTenantId()).isNotEqualTo(tenantB.getId());
            assertThat(result.getDocTitle()).doesNotContain("Bravo");
        }
    }

    @Test
    @DisplayName("Database Layer: Tenant A repository query cannot fetch Tenant B document by ID")
    void testDirectDocRepositoryIsolation() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "alpha-test"));

        Optional<KnowledgeDocEntity> docForAlpha = docRepository.findByIdAndTenantId(tenantBDoc.getId(), tenantA.getId());
        assertThat(docForAlpha).isEmpty();

        List<KnowledgeDocEntity> alphaDocs = docRepository.findByTenantId(tenantA.getId());
        for (KnowledgeDocEntity doc : alphaDocs) {
            assertThat(doc.getTenantId()).isEqualTo(tenantA.getId());
            assertThat(doc.getTenantId()).isNotEqualTo(tenantB.getId());
        }
    }

    @Test
    @DisplayName("Database Layer: Tenant A chunk repository query returns 0 chunks from Tenant B")
    void testDirectChunkRepositoryIsolation() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "alpha-test"));

        List<KnowledgeChunkEntity> alphaChunks = chunkRepository.findByTenantId(tenantA.getId());
        assertThat(alphaChunks).isNotEmpty();
        for (KnowledgeChunkEntity chunk : alphaChunks) {
            assertThat(chunk.getTenantId()).isEqualTo(tenantA.getId());
            assertThat(chunk.getTenantId()).isNotEqualTo(tenantB.getId());
            assertThat(chunk.getDocId()).isNotEqualTo(tenantBDoc.getId());
            assertThat(chunk.getDocId()).isNotEqualTo(tenantBRunbook.getId());
        }
    }

    @Test
    @DisplayName("Deletion Isolation: Tenant A cannot delete Tenant B knowledge document")
    void testTenantCannotDeleteOtherTenantDoc() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantA.getId(), "alpha-test"));

        boolean deleted = ingestionService.deleteDocument(tenantA.getId(), tenantBDoc.getId());
        assertThat(deleted).isFalse();

        // Verify Tenant B's doc still exists intact
        TenantContextHolder.setContext(TenantContext.ofSystem(tenantB.getId(), "bravo-test"));
        Optional<KnowledgeDocEntity> stillExists = docRepository.findByIdAndTenantId(tenantBDoc.getId(), tenantB.getId());
        assertThat(stillExists).isPresent();
    }
}
