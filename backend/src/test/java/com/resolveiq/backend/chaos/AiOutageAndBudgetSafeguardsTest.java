package com.resolveiq.backend.chaos;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos & Fault Injection: AI Provider Outages, Token/Tool Call Budget Safeguards, and Insufficient-Evidence Fallbacks (PRD §§22, 23, 34).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AiOutageAndBudgetSafeguardsTest {

    @Autowired private InvestigationLoopEngine loopEngine;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;

    private UUID tenantId;
    private IncidentEntity testIncident;

    @BeforeEach
    void setUp() {
        OrganizationEntity org = organizationRepository.save(new OrganizationEntity("AI Chaos Org", "aichaos-org", "ENTERPRISE"));
        tenantId = org.getId();

        ProjectEntity proj = projectRepository.save(new ProjectEntity(tenantId, "AI Project", "ai-proj", "desc"));
        testIncident = new IncidentEntity(
                tenantId,
                proj.getId(),
                "fp-aichaos-" + UUID.randomUUID(),
                "AI Chaos Incident",
                IncidentStatus.INVESTIGATING,
                IncidentSeverity.SEV1,
                "checkout-service",
                "[\"checkout-service\"]",
                "Testing loop limits, timeouts, and degradation."
        );
        testIncident = incidentRepository.save(testIncident);
    }

    @Test
    @DisplayName("AI Chaos 1: Hard Enforced Safeguard Ceilings (12 steps, 12 tool calls, 90s timeout)")
    void testHardEnforcedSafeguardCeilings() {
        InvestigationLoopResult result = loopEngine.run(testIncident, tenantId, "Verify safeguard bounds under stress");

        assertThat(result).isNotNull();
        assertThat(result.stepCount()).isLessThanOrEqualTo(InvestigationSafeguards.MAX_STEPS);
        assertThat(result.toolCallCount()).isLessThanOrEqualTo(InvestigationSafeguards.MAX_TOOL_CALLS);
        assertThat(result.durationMs()).isLessThanOrEqualTo(InvestigationSafeguards.TOTAL_TIMEOUT_SECONDS * 1000);
        assertThat(result.structuredRca()).isNotNull();
    }

    @Test
    @DisplayName("AI Chaos 2: Provider Outage / Missing Telemetry Triggers Insufficient Evidence with Uncertainty Statement")
    void testMissingTelemetryEmitsInsufficientEvidence() {
        InvestigationLoopResult result = loopEngine.run(testIncident, tenantId, "INSUFFICIENT_EVIDENCE_TEST: telemetry service unreachable");

        assertThat(result).isNotNull();
        StructuredRcaDto rca = result.structuredRca();
        assertThat(rca).isNotNull();
        assertThat(rca.insufficientEvidence()).isTrue();
        assertThat(rca.uncertaintyStatement())
                .isNotNull()
                .isNotBlank();
        assertThat(rca.summary()).containsIgnoringCase("insufficient");
    }
}
