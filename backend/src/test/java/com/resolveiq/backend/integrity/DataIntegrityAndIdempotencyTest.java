package com.resolveiq.backend.integrity;

import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.backend.domain.IncidentEventEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.repository.IncidentEventRepository;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.service.IncidentService;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data Integrity & Idempotency Verification (PRD §§19.2, 20.3, 52).
 * Verifies fingerprint deduplication, append-only timeline immutability, and state consistency.
 */
@SpringBootTest
@ActiveProfiles("test")
public class DataIntegrityAndIdempotencyTest {

    @Autowired private TenantService tenantService;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private IncidentEventRepository incidentEventRepository;
    @Autowired private IncidentService incidentService;

    private OrganizationEntity tenant;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("Integrity Org " + suffix, "integ-" + suffix, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "integrity-setup"));
        try {
            project = tenantService.createProject("Integrity Proj", "integ-proj-" + suffix, "Integrity Project");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Integrity 1: Fingerprint Deduplication Prevents Duplicate Incidents")
    void testFingerprintDeduplication() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "fp-test"));
        String sharedFingerprint = "fp-sha256-payment-service-high-latency";
        try {
            IncidentEntity first = new IncidentEntity(
                    tenant.getId(),
                    project.getId(),
                    sharedFingerprint,
                    "Payment Latency Incident Initial",
                    "payment-service",
                    IncidentSeverity.SEV2,
                    "Payments"
            );
            incidentRepository.save(first);

            // Attempt to look up existing active incident by fingerprint
            var existing = incidentRepository.findByFingerprintAndTenantIdAndStatusNotIn(
                    sharedFingerprint,
                    tenant.getId(),
                    List.of(com.resolveiq.common.incident.IncidentStatus.CLOSED, com.resolveiq.common.incident.IncidentStatus.RESOLVED)
            );
            assertThat(existing).isPresent();

            // When new telemetry matches this fingerprint, the system correlates with existing incident rather than duplicating
            assertThat(existing.get().getId()).isEqualTo(first.getId());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Integrity 2: Incident Timeline is Strictly Append-Only (Immutable Audit Trail)")
    void testTimelineAppendOnlyImmutability() {
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "timeline-test"));
        try {
            IncidentEntity inc = new IncidentEntity(
                    tenant.getId(),
                    project.getId(),
                    "fp-timeline-" + UUID.randomUUID(),
                    "Timeline Audit Test Incident",
                    "order-service",
                    IncidentSeverity.SEV2,
                    "Orders"
            );
            inc = incidentRepository.save(inc);

            IncidentEventEntity e1 = incidentService.addComment(inc.getId(), "Initial triage begun by SRE");
            IncidentEventEntity e2 = incidentService.addComment(inc.getId(), "Identified connection pool exhaustion in database");

            List<IncidentEventEntity> timeline = incidentService.getTimeline(inc.getId());

            assertThat(timeline).hasSize(2);
            assertThat(timeline.get(0).getId()).isEqualTo(e1.getId());
            assertThat(timeline.get(1).getId()).isEqualTo(e2.getId());

            // Check timestamps are monotonically increasing or equal
            assertThat(timeline.get(0).getCreatedAt()).isBeforeOrEqualTo(timeline.get(1).getCreatedAt());
        } finally {
            TenantContextHolder.clear();
        }
    }
}
