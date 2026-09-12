package com.resolveiq.backend.investigation;

import com.resolveiq.backend.domain.*;
import com.resolveiq.backend.investigation.tools.*;
import com.resolveiq.backend.rag.embedding.DeterministicEmbeddingClient;
import com.resolveiq.backend.rag.embedding.EmbeddingService;
import com.resolveiq.backend.rag.retrieval.BM25Ranker;
import com.resolveiq.backend.rag.retrieval.HybridRetrievalEngine;
import com.resolveiq.backend.rag.retrieval.VectorSimilarityRanker;
import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.backend.repository.*;
import com.resolveiq.common.incident.EvidenceSource;
import com.resolveiq.common.incident.IncidentEventType;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import com.resolveiq.common.knowledge.KnowledgeDocType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class InvestigationToolsTest {

    @Autowired private ToolRegistry toolRegistry;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentEventRepository incidentEventRepository;
    @Autowired private DeploymentRepository deploymentRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private KnowledgeDocRepository knowledgeDocRepository;
    @Autowired private KnowledgeChunkRepository knowledgeChunkRepository;

    private UUID tenantId;
    private UUID incidentId;

    @BeforeEach
    void setUp() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("Tools Org", "tools-org", "ENTERPRISE"));
        tenantId = org.getId();

        ProjectEntity proj = projectRepository.save(new ProjectEntity(tenantId, "Tools Proj", "tools-proj", "desc"));
        ServiceEntity paymentService = serviceRepository.save(new ServiceEntity(tenantId, proj.getId(), "payment-service", "TIER_1", "payments-team", "repo"));

        IncidentEntity incident = new IncidentEntity(
                tenantId,
                proj.getId(),
                "tools-test-fingerprint-" + UUID.randomUUID(),
                "Checkout Failure Investigation",
                IncidentStatus.INVESTIGATING,
                IncidentSeverity.SEV1,
                "payment-service",
                "[\"payment-service\", \"order-service\"]",
                "High 5xx error rate observed."
        );
        incident = incidentRepository.save(incident);
        incidentId = incident.getId();

        // Seed incident timeline
        incidentEventRepository.save(new IncidentEventEntity(
                tenantId, incidentId, IncidentEventType.ANOMALY_DETECTED, "SYSTEM", null,
                "5xx error rate exceeded 5%", "{}", null
        ));

        // Seed deployment
        deploymentRepository.save(new DeploymentEntity(
                tenantId, proj.getId(), paymentService.getId(), "payment-service",
                "production", "v2.8", "d7a4b81", "feat(pool): update hikari connection pool",
                "ci-deployer", "SUCCESS", Instant.now().minusSeconds(600), "{}"
        ));

        // Seed runbook knowledge
        KnowledgeDocEntity doc = new KnowledgeDocEntity(
                tenantId, proj.getId(), KnowledgeDocType.RUNBOOK,
                "Payment Service Database Failover Runbook", "docs/runbook.md",
                "payment-service", "production",
                "# Payment Service Database Failover Runbook\n\nInstructions to restart Hikari pool and scale replicas.",
                "hash-rb-1", "{}"
        );
        doc = knowledgeDocRepository.save(doc);

        KnowledgeChunkEntity chunk = new KnowledgeChunkEntity(
                tenantId, doc.getId(), 0,
                "[payment-service > runbook] When Hikari pool exhausted, scale replica or increase maximum-pool-size.",
                25, null, "payment-service > runbook"
        );
        chunk.setEmbeddingVector(new float[384]);
        knowledgeChunkRepository.save(chunk);
    }

    @Test
    @DisplayName("Verify all 11 tools are registered in ToolRegistry")
    void testAllElevenToolsRegistered() {
        Set<String> names = toolRegistry.getRegisteredToolNames();
        assertEquals(11, names.size(), "All 11 PRD §25 tools must be registered");

        assertTrue(names.contains("queryMetrics"));
        assertTrue(names.contains("searchLogs"));
        assertTrue(names.contains("inspectTrace"));
        assertTrue(names.contains("getServiceDependencies"));
        assertTrue(names.contains("getRecentDeployments"));
        assertTrue(names.contains("getIncidentTimeline"));
        assertTrue(names.contains("searchHistoricalIncidents"));
        assertTrue(names.contains("searchRunbooks"));
        assertTrue(names.contains("getServiceHealth"));
        assertTrue(names.contains("getConfigurationChanges"));
        assertTrue(names.contains("getCodeChanges"));
    }

    @Test
    @DisplayName("Tool: queryMetrics returns metric data and emits metric anomaly evidence")
    void testQueryMetricsTool() {
        ToolExecutionResult result = toolRegistry.execute(tenantId, incidentId, "queryMetrics",
                Map.of("service", "payment-service", "metric", "error_rate", "timeRange", "1h"));

        assertTrue(result.success());
        assertNotNull(result.formattedResult());
        assertTrue(result.formattedResult().contains("<telemetry_data source=\"metrics\""));
        assertFalse(result.discoveredEvidence().isEmpty());
        assertEquals(EvidenceSource.METRICS, result.discoveredEvidence().get(0).source());
    }

    @Test
    @DisplayName("Tool: searchLogs returns log entries and defuses injection")
    void testSearchLogsTool() {
        ToolExecutionResult result = toolRegistry.execute(tenantId, incidentId, "searchLogs",
                Map.of("service", "payment-service", "query", "connection pool", "severity", "ERROR"));

        assertTrue(result.success());
        assertTrue(result.formattedResult().contains("<telemetry_data source=\"logs\""));
        assertFalse(result.discoveredEvidence().isEmpty());
        assertEquals(EvidenceSource.LOGS, result.discoveredEvidence().get(0).source());
    }

    @Test
    @DisplayName("Tool: inspectTrace returns multi-hop distributed span waterfall")
    void testInspectTraceTool() {
        ToolExecutionResult result = toolRegistry.execute(tenantId, incidentId, "inspectTrace",
                Map.of("traceId", "4bf92f3577b34da6a3ce929d0e0e4736"));

        assertTrue(result.success());
        assertTrue(result.formattedResult().contains("failingService=\"payment-service\""));
        assertEquals(EvidenceSource.TRACES, result.discoveredEvidence().get(0).source());
    }

    @Test
    @DisplayName("Tool: getRecentDeployments returns recent releases")
    void testGetRecentDeploymentsTool() {
        ToolExecutionResult result = toolRegistry.execute(tenantId, incidentId, "getRecentDeployments",
                Map.of("service", "payment-service", "timeRange", "2h"));

        assertTrue(result.success());
        assertTrue(result.formattedResult().contains("version=\"v2.8\""));
        assertEquals(EvidenceSource.DEPLOYMENT, result.discoveredEvidence().get(0).source());
    }

    @Test
    @DisplayName("Tool: searchRunbooks returns relevant runbook from hybrid RAG")
    void testSearchRunbooksTool() {
        ToolExecutionResult result = toolRegistry.execute(tenantId, incidentId, "searchRunbooks",
                Map.of("query", "Hikari pool exhausted failover", "limit", 3));

        assertTrue(result.success());
        assertTrue(result.formattedResult().contains("<rag_knowledge domain=\"runbooks\""));
    }

    @Test
    @DisplayName("Tool: missing required parameters returns failure cleanly without throwing")
    void testToolMissingParametersReturnsFailure() {
        ToolExecutionResult result = toolRegistry.execute(tenantId, incidentId, "queryMetrics", Collections.emptyMap());
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Missing required parameters"));
    }

    @Test
    @DisplayName("Tool: unregistered tool name returns failure with list of registered tools")
    void testUnregisteredToolReturnsFailure() {
        ToolExecutionResult result = toolRegistry.execute(tenantId, incidentId, "executeArbitrarySql",
                Map.of("query", "DROP TABLE users;"));
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("not registered"));
    }
}
