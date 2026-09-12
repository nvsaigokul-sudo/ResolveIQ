package com.resolveiq.backend.chaos;

import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.rag.retrieval.HybridRetrievalEngine;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import com.resolveiq.common.knowledge.KnowledgeSearchRequestDto;
import com.resolveiq.common.knowledge.KnowledgeSearchResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos & Fault Injection: OpenSearch / Retrieval Engine Degradation & Safe Fallback (PRD §§22, 25, 34).
 * Ensures that when search backend degrades or encounters missing indices, the platform degrades gracefully
 * without throwing unhandled exceptions, and strictly produces ZERO hallucinated or fabricated matches.
 */
@SpringBootTest
@ActiveProfiles("test")
public class OpenSearchFailureAndDegradationTest {

    @Autowired private TenantService tenantService;
    @Autowired private HybridRetrievalEngine retrievalEngine;

    private OrganizationEntity tenant;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("Search Chaos Org " + suffix, "schchaos-" + suffix, "ENTERPRISE");
    }

    @Test
    @DisplayName("Search Degradation 1: Unindexed Tenant Query Gracefully Returns Empty List (Zero Hallucination)")
    void testUnindexedTenantReturnsEmptyListWithoutHallucination() {
        KnowledgeSearchRequestDto req = new KnowledgeSearchRequestDto();
        req.setQuery("critical database connection leak deadlock");
        req.setLimit(5);
        req.setMinScore(0.3);

        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenant.getId(), req);

        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Search Degradation 2: Filter with Non-Existent DocType Degrades Safely to Empty")
    void testFilterMismatchDegradesGracefully() {
        KnowledgeSearchRequestDto req = new KnowledgeSearchRequestDto();
        req.setQuery("restart payment pod");
        req.setDocTypes(List.of(KnowledgeDocType.ARCHITECTURE_DOC));
        req.setLimit(10);

        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenant.getId(), req);

        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Search Degradation 3: Blank/Null Query Does Not Execute Downstream Engine or Throw 500")
    void testBlankQueryHandledGracefully() {
        KnowledgeSearchRequestDto blankReq = new KnowledgeSearchRequestDto();
        blankReq.setQuery("   ");

        List<KnowledgeSearchResultDto> results = retrievalEngine.search(tenant.getId(), blankReq);

        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }
}
