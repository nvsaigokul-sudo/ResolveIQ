package com.resolveiq.backend.investigation;

import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.investigation.dto.StructuredRcaDto;
import com.resolveiq.backend.investigation.loop.InvestigationLoopEngine;
import com.resolveiq.backend.investigation.loop.InvestigationLoopResult;
import com.resolveiq.backend.investigation.loop.InvestigationSafeguards;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.repository.OrganizationRepository;
import com.resolveiq.backend.repository.ProjectRepository;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class InvestigationLoopSafeguardsTest {

    @Autowired private InvestigationLoopEngine loopEngine;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;

    private UUID tenantId;
    private IncidentEntity incident;

    @BeforeEach
    void setUp() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("Safeguard Org", "safeguard-org", "ENTERPRISE"));
        tenantId = org.getId();

        ProjectEntity proj = projectRepository.save(new ProjectEntity(tenantId, "Safeguard Proj", "safeguard-proj", "desc"));
        incident = new IncidentEntity(
                tenantId,
                proj.getId(),
                "safeguard-fp-" + UUID.randomUUID(),
                "Safeguard Test Incident",
                IncidentStatus.INVESTIGATING,
                IncidentSeverity.SEV2,
                "payment-service",
                "[\"payment-service\"]",
                "Testing loop limits and safeguards."
        );
        incident = incidentRepository.save(incident);
    }

    @Test
    @DisplayName("Investigation loop adheres to step budget and tool call budget")
    void testLoopAdheresToBudgets() {
        InvestigationLoopResult result = loopEngine.run(incident, tenantId, "Standard incident verification");

        assertNotNull(result);
        assertTrue(result.stepCount() <= InvestigationSafeguards.MAX_STEPS,
                "Step count must not exceed max steps of " + InvestigationSafeguards.MAX_STEPS);
        assertTrue(result.toolCallCount() <= InvestigationSafeguards.MAX_TOOL_CALLS,
                "Tool call count must not exceed max tool calls of " + InvestigationSafeguards.MAX_TOOL_CALLS);
        assertTrue(result.durationMs() < InvestigationSafeguards.TOTAL_TIMEOUT_SECONDS * 1000,
                "Duration must be within 90s timeout");
        assertNotNull(result.structuredRca());
    }

    @Test
    @DisplayName("First-class INSUFFICIENT EVIDENCE handling when telemetry is missing")
    void testInsufficientEvidenceHandling() {
        InvestigationLoopResult result = loopEngine.run(incident, tenantId, "INSUFFICIENT_EVIDENCE_TEST: simulate missing telemetry");

        assertNotNull(result);
        StructuredRcaDto rca = result.structuredRca();
        assertNotNull(rca);
        assertTrue(rca.insufficientEvidence(), "Must emit insufficientEvidence=true when telemetry is inconclusive or absent");
        assertNotNull(rca.uncertaintyStatement());
        assertFalse(rca.uncertaintyStatement().isBlank(), "Uncertainty statement must explain missing evidence");
    }

    @Test
    @DisplayName("Safeguard constants match PRD §22 & §23 requirements")
    void testSafeguardConstantsMatchPrd() {
        assertEquals(12, InvestigationSafeguards.MAX_STEPS, "Max steps must be 12 per PRD");
        assertEquals(12, InvestigationSafeguards.MAX_TOOL_CALLS, "Max tool calls must be 12 per PRD");
        assertEquals(90, InvestigationSafeguards.TOTAL_TIMEOUT_SECONDS, "Total timeout must be 90s per PRD");
        assertEquals(10, InvestigationSafeguards.PER_TOOL_TIMEOUT_SECONDS, "Per-tool timeout must be 10s per PRD");
    }
}
