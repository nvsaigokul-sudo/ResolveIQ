package com.resolveiq.backend.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.EvidenceEntity;
import com.resolveiq.backend.domain.IncidentEntity;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.domain.ProjectEntity;
import com.resolveiq.backend.repository.EvidenceRepository;
import com.resolveiq.backend.repository.IncidentRepository;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.correlation.BlastRadiusResult;
import com.resolveiq.common.correlation.CandidateOriginScore;
import com.resolveiq.common.correlation.ConfidenceTier;
import com.resolveiq.common.correlation.CorrelatedIncidentPayload;
import com.resolveiq.common.correlation.CorrelationSignalBreakdown;
import com.resolveiq.common.incident.IncidentSeverity;
import com.resolveiq.common.incident.IncidentStatus;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.common.tenant.TenantContextHolder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KafkaIncidentConsumerIntegrationTest {

    @Autowired
    private IncidentKafkaConsumer incidentKafkaConsumer;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrganizationEntity tenant;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        String slug = "kfk-org-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("Kafka Incident Org", slug, "ENTERPRISE");

        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "test-kfk-setup"));
        try {
            project = tenantService.createProject("Kafka Project", "kfk-" + slug, "Test project");
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("IncidentKafkaConsumer consumes CorrelatedIncidentPayload envelope, establishes tenant context, and persists incident with evidence (PRD §19, §21, §51)")
    void testKafkaIncidentConsumption() throws Exception {
        String fingerprint = "fp-kfk-" + UUID.randomUUID();
        BlastRadiusResult blast = new BlastRadiusResult("payment-service", Set.of("order-service"), 1, 1, 25.0);
        CandidateOriginScore origin = new CandidateOriginScore("payment-service", 0.90, 0.95, 0.85, 0.90, Instant.now());
        CorrelationSignalBreakdown breakdown = CorrelationSignalBreakdown.compute(0.9, 0.9, 0.9, 0.8, 0.7, 0.5, 0.5, 0.5);

        CorrelatedIncidentPayload payload = new CorrelatedIncidentPayload(
                UUID.randomUUID(),
                fingerprint,
                "Critical Payment Gateway Failure",
                "payment-service",
                Severity.CRITICAL,
                "DETECTED",
                UUID.randomUUID(),
                Collections.emptyList(),
                ConfidenceTier.CONFIRMED_RELATIONSHIP,
                0.92,
                breakdown,
                blast,
                List.of(origin),
                Instant.now()
        );

        KafkaEventEnvelope<CorrelatedIncidentPayload> envelope = KafkaEventEnvelope.create(
                tenant.getId(),
                project.getId(),
                "production",
                UUID.randomUUID().toString(),
                null,
                payload
        );

        String message = objectMapper.writeValueAsString(envelope);
        String partitionKey = tenant.getId() + ":payment-service";

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "incidents", 0, 100L, partitionKey, message
        );

        // Execute consumption
        incidentKafkaConsumer.consumeIncidentEvent(record);

        // Verify incident persisted in database under correct tenant
        TenantContextHolder.setContext(TenantContext.ofSystem(tenant.getId(), "verify-kfk"));
        try {
            List<IncidentEntity> incidents = incidentRepository.findAllByTenantId(tenant.getId());
            assertThat(incidents).isNotEmpty();
            IncidentEntity found = incidents.get(0);
            assertThat(found.getFingerprint()).isEqualTo(fingerprint);
            assertThat(found.getRootService()).isEqualTo("payment-service");
            assertThat(found.getSeverity()).isEqualTo(IncidentSeverity.SEV1);
            assertThat(found.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);

            // Verify evidence was automatically assembled and persisted
            List<EvidenceEntity> evidence = evidenceRepository.findByIncidentIdAndTenantIdOrderByCreatedAtDesc(found.getId(), tenant.getId());
            assertThat(evidence).isNotEmpty();
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @DisplayName("IncidentKafkaConsumer discards malformed or missing tenant payload without crashing (PRD §15.1)")
    void testMalformedMessageHandling() {
        ConsumerRecord<String, String> invalidRecord = new ConsumerRecord<>(
                "incidents", 0, 101L, "invalid-key", "{\"invalid_json\": true}"
        );

        // Should not throw or crash
        incidentKafkaConsumer.consumeIncidentEvent(invalidRecord);
    }
}
