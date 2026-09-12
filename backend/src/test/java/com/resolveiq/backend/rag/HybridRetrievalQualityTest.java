package com.resolveiq.backend.rag;

import com.resolveiq.backend.domain.KnowledgeDocEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.rag.retrieval.HybridRetrievalEngine;
import com.resolveiq.backend.rag.service.KnowledgeIngestionService;
import com.resolveiq.backend.service.TenantService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class HybridRetrievalQualityTest {

    @Autowired
    private KnowledgeIngestionService ingestionService;

    @Autowired
    private HybridRetrievalEngine retrievalEngine;

    @Autowired
    private TenantService tenantService;

    private OrganizationEntity tenant;

    @BeforeEach
    void setUp() {
        tenant = tenantService.createOrganization("Acme RAG Corp", "acme-rag-" + UUID.randomUUID().toString().substring(0, 8), "ENTERPRISE");
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "rag-test-setup"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Exact keyword recall ranks exact error code document at top")
    void testExactKeywordRecall() {
        ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.POSTMORTEM,
                "Postmortem: Redis Latency Spike", "https://wiki/postmortem-redis",
                "redis-cache", "prod",
                "# Postmortem: Redis Cluster Eviction\nError code ERR_REDIS_OOM_9082 occurred during peak cache invalidation.",
                null
        );

        ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.POSTMORTEM,
                "Postmortem: Database Pool Outage", "https://wiki/postmortem-db",
                "payment-service", "prod",
                "# Postmortem: HikariCP Connection Exhaustion\nError code ERR_HIKARI_TIMEOUT_4011 occurred on postgres primary.",
                null
        );

        KnowledgeSearchRequestDto request = new KnowledgeSearchRequestDto(
                "ERR_HIKARI_TIMEOUT_4011", null, null, 5
        );

        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenant.getId(), request);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getDocTitle()).isEqualTo("Postmortem: Database Pool Outage");
        assertThat(results.get(0).getBm25Score()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("Semantic paraphrase recall retrieves conceptually related document without exact keyword match")
    void testSemanticParaphraseRecall() {
        ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.RUNBOOK,
                "Postgres Connection Saturation Playbook", "https://runbooks/postgres-saturation",
                "payment-service", "prod",
                "# Postgres Connection Saturation Remediation\nWhen database connection pool is full and queries queue up, kill idle transactions and restart pool.",
                null
        );

        ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.RUNBOOK,
                "Frontend Asset Deployment Guide", "https://runbooks/frontend-cdn",
                "web-client", "prod",
                "# CDN Invalidation\nPurge Cloudflare edge cache and recompile static assets.",
                null
        );

        // Query does not use the exact phrase "connection saturation", but semantically targets db pool exhaustion
        KnowledgeSearchRequestDto request = new KnowledgeSearchRequestDto(
                "database pool exhausted and threads waiting for db connection", null, null, 5
        );
        request.setAlphaWeight(0.70); // Emphasize semantic vector similarity

        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenant.getId(), request);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getDocTitle()).isEqualTo("Postgres Connection Saturation Playbook");
        assertThat(results.get(0).getVectorScore()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("Combined keyword and semantic signals rank multi-signal candidate highest")
    void testHybridCombinedRanking() {
        ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.POSTMORTEM,
                "Payment Service v2.8 Connection Pool Leak", "https://wiki/postmortem-payment-v28",
                "payment-service", "prod",
                "# Postmortem: Payment Service v2.8 Connection Pool Regression\n" +
                "Deployment of v2.8 introduced an unclosed database transaction in the refund flow causing database connection exhaustion.",
                null
        );

        ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.POSTMORTEM,
                "Order Service Generic Timeout", "https://wiki/postmortem-order-timeout",
                "order-service", "prod",
                "# Postmortem: Order Service HTTP 504\nUpstream gateway experienced transient network timeouts.",
                null
        );

        KnowledgeSearchRequestDto request = new KnowledgeSearchRequestDto(
                "v2.8 payment database connection leak", null, null, 5
        );
        request.setAlphaWeight(0.65);

        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenant.getId(), request);

        assertThat(results).isNotEmpty();
        KnowledgeSearchResultDto top = results.get(0);
        assertThat(top.getDocTitle()).isEqualTo("Payment Service v2.8 Connection Pool Leak");
        assertThat(top.getCompositeScore()).isGreaterThan(0.60);
        assertThat(top.getInertXmlRepresentation()).contains("<rag_knowledge");
    }

    @Test
    @DisplayName("Filters search results by docType and service correctly")
    void testFilteredSearchByDocTypeAndService() {
        ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.RUNBOOK,
                "Payment Service Restart Runbook", "https://runbooks/payment-restart",
                "payment-service", "prod",
                "# Payment Service Restart Steps\nHow to safely restart payment-service instances.",
                null
        );

        ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.POSTMORTEM,
                "Payment Service Prior Outage", "https://wiki/payment-prior-outage",
                "payment-service", "prod",
                "# Historical Incident: Payment Outage\nPrior outage analysis for payment-service.",
                null
        );

        // Search specifically for RUNBOOK only
        KnowledgeSearchRequestDto runbookRequest = new KnowledgeSearchRequestDto(
                "restart payment service", List.of(KnowledgeDocType.RUNBOOK), "payment-service", 5
        );

        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenant.getId(), runbookRequest);
        assertThat(results).isNotEmpty();
        for (KnowledgeSearchResultDto r : results) {
            assertThat(r.getDocType()).isEqualTo(KnowledgeDocType.RUNBOOK);
            assertThat(r.getService()).isEqualTo("payment-service");
        }
    }

    @Test
    @DisplayName("Deduplication by content hash updates document rather than creating duplicate chunks")
    void testDeduplicationByContentHash() {
        String content = "# Unique Runbook\nThis is unique content for testing deduplication.";

        KnowledgeDocEntity doc1 = ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.RUNBOOK,
                "Runbook v1", "https://wiki/runbook", "auth-service", "prod", content, null
        );

        KnowledgeDocEntity doc2 = ingestionService.ingestDocument(
                tenant.getId(), null, KnowledgeDocType.RUNBOOK,
                "Runbook v1 Renamed", "https://wiki/runbook", "auth-service", "prod", content, null
        );

        assertThat(doc1.getId()).isEqualTo(doc2.getId());
        assertThat(doc2.getTitle()).isEqualTo("Runbook v1 Renamed");
    }
}
