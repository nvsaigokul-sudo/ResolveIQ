package com.resolveiq.backend.security;

import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.backend.domain.IncidentEventEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.repository.IncidentEventRepository;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.incident.IncidentEventType;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IncidentTimelineImmutabilityTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentEventRepository incidentEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OrganizationEntity tenant;
    private ProjectEntity project;
    private IncidentEntity incident;

    @BeforeEach
    void setUp() {
        String slug = "immut-org-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("Timeline Immutability Org", slug, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-immut-setup"));
        try {
            project = tenantService.createProject("Immutability Project", "immut-" + slug, "Test project");
            incident = new IncidentEntity(
                    tenant.getId(),
                    project.getId(),
                    "fingerprint-immut-" + UUID.randomUUID(),
                    "Immutable incident",
                    "payment-service",
                    IncidentSeverity.SEV2,
                    "Payment Team"
            );
            incident.setStatus(IncidentStatus.INVESTIGATING);
            incident = incidentRepository.save(incident);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Acceptance Criterion: Incident timeline is strictly append-only (UPDATE and DELETE prohibited at DB engine level per PRD §20)")
    void testIncidentTimelineImmutability() {
        UUID eventId;
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-immut"));
        try {
            IncidentEventEntity event = new IncidentEventEntity(
                    tenant.getId(),
                    incident.getId(),
                    IncidentEventType.INVESTIGATION_STARTED,
                    "SYSTEM",
                    null,
                    "Investigation started by autonomous engine.",
                    "{\"trigger\": \"alert_threshold\"}",
                    null
            );
            eventId = incidentEventRepository.save(event).getId();
        } finally {
            TenantContextHolder.clear();
        }

        assertThat(eventId).isNotNull();

        // 1. Direct SQL UPDATE must fail due to immutability trigger
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "UPDATE incident_events SET summary = 'Tampered timeline narrative' WHERE id = ?",
                    eventId
            );
        }).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("strictly immutable");

        // 2. Direct SQL DELETE must fail due to immutability trigger
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "DELETE FROM incident_events WHERE id = ?",
                    eventId
            );
        }).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("strictly immutable");

        // 3. Verify original event remains completely untouched
        String currentSummary = jdbcTemplate.queryForObject(
                "SELECT summary FROM incident_events WHERE id = ?",
                String.class,
                eventId
        );
        assertThat(currentSummary).isEqualTo("Investigation started by autonomous engine.");
    }
}
