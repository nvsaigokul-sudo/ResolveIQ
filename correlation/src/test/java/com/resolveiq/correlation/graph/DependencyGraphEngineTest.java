package com.resolveiq.correlation.graph;

import com.resolveiq.common.correlation.BlastRadiusResult;
import com.resolveiq.common.correlation.CandidateOriginScore;
import com.resolveiq.common.telemetry.AnomalyLifecycleState;
import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.model.AnomalyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dynamic Service Dependency Graph Tests (PRD §19).
 * Verifies topology construction from distributed traces, cycle handling,
 * blast radius calculation, candidate origin scoring, and tenant isolation.
 */
class DependencyGraphEngineTest {

    private DependencyGraphService graphService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        graphService = new DependencyGraphService();
        tenantId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Graph Construction: Builds multi-tier directed topology from distributed trace spans")
    void testTopologyConstructionFromSpans() {
        Instant now = Instant.now();
        String traceId = "trace-order-checkout-001";

        // Span 1: api-gateway (root span, no parent)
        graphService.ingestSpan(tenantId, traceId, "span-1", null, "api-gateway", false, 120.0, now.minusMillis(100));

        // Span 2: order-service (child of span-1)
        graphService.ingestSpan(tenantId, traceId, "span-2", "span-1", "order-service", false, 90.0, now.minusMillis(80));

        // Span 3: payment-service (child of span-2)
        graphService.ingestSpan(tenantId, traceId, "span-3", "span-2", "payment-service", false, 60.0, now.minusMillis(50));

        // Span 4: notification-service (child of span-2)
        graphService.ingestSpan(tenantId, traceId, "span-4", "span-2", "notification-service", false, 25.0, now.minusMillis(30));

        TenantDependencyGraph graph = graphService.getOrCreateGraph(tenantId);
        assertThat(graph.getAllServiceIds()).containsExactlyInAnyOrder(
                "api-gateway", "order-service", "payment-service", "notification-service"
        );

        // Verify shortest path topological distances
        assertThat(graphService.getShortestDistance(tenantId, "api-gateway", "order-service")).isEqualTo(1);
        assertThat(graphService.getShortestDistance(tenantId, "api-gateway", "payment-service")).isEqualTo(2);
        assertThat(graphService.getShortestDistance(tenantId, "api-gateway", "notification-service")).isEqualTo(2);
        assertThat(graphService.getShortestDistance(tenantId, "payment-service", "notification-service")).isEqualTo(2); // via order-service
        assertThat(graphService.getShortestDistance(tenantId, "api-gateway", "api-gateway")).isEqualTo(0);
    }

    @Test
    @DisplayName("Cycle Handling: Detects circular dependency, marks cycle edge, and safely completes traversals")
    void testCircularDependencyHandling() {
        Instant now = Instant.now();
        // Create cyclic topology: Service A -> Service B -> Service C -> Service A
        graphService.recordServiceCall(tenantId, "svc-auth", "svc-session", false, 20.0, now);
        graphService.recordServiceCall(tenantId, "svc-session", "svc-token", false, 15.0, now);
        graphService.recordServiceCall(tenantId, "svc-token", "svc-auth", false, 25.0, now); // Back-edge creating cycle!

        TenantDependencyGraph graph = graphService.getOrCreateGraph(tenantId);
        ServiceNode tokenNode = graph.getNode("svc-token").orElseThrow();

        // Cycle edge must be detected and flagged
        assertThat(tokenNode.getOutgoingEdges().get("svc-auth").isCycleEdge()).isTrue();

        // Traversals must terminate cleanly without infinite loops or stack overflow
        int distance = graphService.getShortestDistance(tenantId, "svc-auth", "svc-token");
        assertThat(distance).isEqualTo(1); // Connected via direct or reverse edge

        BlastRadiusResult blast = graphService.calculateBlastRadius(tenantId, "svc-auth");
        assertThat(blast.impactedServices()).contains("svc-token", "svc-session");
    }

    @Test
    @DisplayName("Blast Radius: Accurately computes direct and transitive downstream dependents")
    void testBlastRadiusCalculation() {
        Instant now = Instant.now();
        // Topology:
        // payment-gateway (root dependency)
        //   <- payment-service (calls gateway)
        //     <- order-service (calls payment-service)
        //       <- api-gateway (calls order-service)
        //   <- refund-service (calls gateway)
        // unrelated-analytics (isolated)

        graphService.recordServiceCall(tenantId, "payment-service", "payment-gateway", false, 40.0, now);
        graphService.recordServiceCall(tenantId, "order-service", "payment-service", false, 50.0, now);
        graphService.recordServiceCall(tenantId, "api-gateway", "order-service", false, 80.0, now);
        graphService.recordServiceCall(tenantId, "refund-service", "payment-gateway", false, 35.0, now);
        graphService.recordServiceCall(tenantId, "unrelated-analytics", "analytics-db", false, 10.0, now);

        BlastRadiusResult blast = graphService.calculateBlastRadius(tenantId, "payment-gateway");

        // Direct callers: payment-service, refund-service
        assertThat(blast.directDownstreamCount()).isEqualTo(2);

        // Total transitive callers impacted: payment-service, refund-service, order-service, api-gateway (4 total)
        assertThat(blast.totalDownstreamCount()).isEqualTo(4);
        assertThat(blast.impactedServices()).containsExactlyInAnyOrder(
                "payment-service", "refund-service", "order-service", "api-gateway"
        );
        // unrelated-analytics is unaffected
        assertThat(blast.impactedServices()).doesNotContain("unrelated-analytics", "analytics-db");
    }

    @Test
    @DisplayName("Candidate Origin Scoring: Ranks deepest failing dependency with earliest error as #1 origin")
    void testCandidateOriginScoring() {
        Instant t0 = Instant.parse("2026-09-12T10:00:00Z");

        // Topology: api-gateway -> order-service -> payment-service
        graphService.recordServiceCall(tenantId, "api-gateway", "order-service", false, 100.0, t0);
        graphService.recordServiceCall(tenantId, "order-service", "payment-service", false, 80.0, t0);

        // Three cascading anomalies:
        // 1. payment-service has CRITICAL connection pool failure at T=t0
        AnomalyRecord aPayment = new AnomalyRecord(
                UUID.randomUUID(), "fp-1", tenantId, null, "payment-service", "db_connections",
                "production", "rule.resource_utilization", DetectorType.RULE_BASED, Severity.CRITICAL,
                AnomalyLifecycleState.RAISED, 98.0, 85.0, null, t0,
                Map.of("message", "HikariPool-1 connection timeout")
        );

        // 2. order-service has HIGH 503 error rate at T=t0 + 15s (due to payment failure)
        AnomalyRecord aOrder = new AnomalyRecord(
                UUID.randomUUID(), "fp-2", tenantId, null, "order-service", "error_rate",
                "production", "rule.error_rate_5xx", DetectorType.RULE_BASED, Severity.HIGH,
                AnomalyLifecycleState.RAISED, 12.0, 5.0, null, t0.plusSeconds(15),
                Map.of("message", "503 downstream payment service unavailable")
        );

        // 3. api-gateway has HIGH 504 timeout at T=t0 + 35s (due to order delay)
        AnomalyRecord aGateway = new AnomalyRecord(
                UUID.randomUUID(), "fp-3", tenantId, null, "api-gateway", "latency_p99",
                "production", "rule.latency_threshold", DetectorType.RULE_BASED, Severity.HIGH,
                AnomalyLifecycleState.RAISED, 2500.0, 500.0, null, t0.plusSeconds(35),
                Map.of("message", "504 Gateway Timeout")
        );

        List<CandidateOriginScore> scores = graphService.scoreOriginCandidates(
                tenantId, List.of(aGateway, aOrder, aPayment));

        assertThat(scores).hasSize(3);

        // Candidate #1 MUST be payment-service because:
        // 1. Earliest anomaly onset (temporal = 1.0)
        // 2. Deepest in call hierarchy: order-service and api-gateway depend on it (topology = 1.0)
        // 3. Highest severity (CRITICAL = 1.0)
        CandidateOriginScore topCandidate = scores.get(0);
        assertThat(topCandidate.serviceId()).isEqualTo("payment-service");
        assertThat(topCandidate.totalOriginScore()).isGreaterThan(0.90);

        // Second candidate is order-service
        assertThat(scores.get(1).serviceId()).isEqualTo("order-service");

        // Third candidate is api-gateway (edge/leaf caller)
        assertThat(scores.get(2).serviceId()).isEqualTo("api-gateway");
        assertThat(scores.get(2).totalOriginScore()).isLessThan(topCandidate.totalOriginScore());
    }

    @Test
    @DisplayName("Tenant Isolation: Tenant Alpha topology is 100% isolated from Tenant Beta")
    void testTenantGraphIsolation() {
        UUID tenantAlpha = UUID.randomUUID();
        UUID tenantBeta = UUID.randomUUID();
        Instant now = Instant.now();

        // Alpha services
        graphService.recordServiceCall(tenantAlpha, "alpha-frontend", "alpha-backend", false, 50.0, now);

        // Beta services
        graphService.recordServiceCall(tenantBeta, "beta-frontend", "beta-backend", false, 50.0, now);

        TenantDependencyGraph graphAlpha = graphService.getOrCreateGraph(tenantAlpha);
        TenantDependencyGraph graphBeta = graphService.getOrCreateGraph(tenantBeta);

        assertThat(graphAlpha.getAllServiceIds()).containsExactlyInAnyOrder("alpha-frontend", "alpha-backend");
        assertThat(graphBeta.getAllServiceIds()).containsExactlyInAnyOrder("beta-frontend", "beta-backend");

        // Distance across tenants returns disconnected (-1)
        assertThat(graphService.getShortestDistance(tenantAlpha, "alpha-frontend", "beta-backend")).isEqualTo(-1);
    }
}
