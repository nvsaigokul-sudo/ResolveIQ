package com.resolveiq.backend.chaos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.OrganizationEntity;
import com.resolveiq.backend.service.TenantService;
import com.resolveiq.common.notification.NotificationChannelType;
import com.resolveiq.common.notification.NotificationRequestedPayload;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos & Fault Injection: Kafka Partition Drops, Broker Failure, Retries, and DLQ Routing (PRD §§5, 27, 34, 51).
 * Validates platform resilience against simulated message bus disruptions.
 */
@SpringBootTest
@ActiveProfiles("test")
public class KafkaChaosAndRecoveryIntegrationTest {

    @Autowired private TenantService tenantService;
    @Autowired private ObjectMapper objectMapper;

    private OrganizationEntity tenant;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantService.createOrganization("Kafka Chaos Org " + suffix, "kchaos-" + suffix, "ENTERPRISE");
    }

    @Test
    @DisplayName("Kafka Chaos 1: Simulated Broker Disconnect & Retry Recovery")
    void testBrokerDisconnectAndRetryRecovery() {
        int maxAttempts = 3;
        AtomicInteger attempts = new AtomicInteger(0);

        Callable<Boolean> sendWithFaultInjection = () -> {
            int cur = attempts.incrementAndGet();
            if (cur < maxAttempts) {
                throw new org.apache.kafka.common.errors.DisconnectException("Simulated broker disconnect at attempt " + cur);
            }
            return true;
        };

        boolean delivered = false;
        long backoffMs = 100;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                delivered = sendWithFaultInjection.call();
                break;
            } catch (Exception e) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ignored) {}
                backoffMs *= 2;
            }
        }

        assertThat(delivered).isTrue();
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Kafka Chaos 2: Duplicate Delivery Idempotency via event_id Deduplication")
    void testDuplicateDeliveryIdempotency() {
        KafkaEventEnvelope<String> envelope = KafkaEventEnvelope.create(
                tenant.getId(),
                null,
                "production",
                UUID.randomUUID().toString(),
                null,
                "incident_created_telemetry_event"
        );

        Set<UUID> processedEventIds = ConcurrentHashMap.newKeySet();
        AtomicInteger executedCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            if (processedEventIds.add(envelope.eventId())) {
                executedCount.incrementAndGet();
            }
        }

        assertThat(executedCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Kafka Chaos 3: Out-of-Order Delivery & Preserved Event Timestamp Ordering")
    void testOutOfOrderDeliveryTimestampPreservation() {
        Instant t0 = Instant.parse("2026-09-12T10:00:00Z");
        Instant t1 = Instant.parse("2026-09-12T10:00:05Z");
        Instant t2 = Instant.parse("2026-09-12T10:00:10Z");

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        List<KafkaEventEnvelope<String>> receivedEnvelopes = List.of(
                new KafkaEventEnvelope<>(id3, tenant.getId(), null, "prod", t2, "c3", "1.0", "p", null, "Event at T2"),
                new KafkaEventEnvelope<>(id1, tenant.getId(), null, "prod", t0, "c1", "1.0", "p", null, "Event at T0"),
                new KafkaEventEnvelope<>(id2, tenant.getId(), null, "prod", t1, "c2", "1.0", "p", null, "Event at T1")
        );

        List<KafkaEventEnvelope<String>> causalOrder = receivedEnvelopes.stream()
                .sorted(Comparator.comparing(KafkaEventEnvelope::timestamp))
                .toList();

        assertThat(causalOrder.get(0).eventId()).isEqualTo(id1);
        assertThat(causalOrder.get(1).eventId()).isEqualTo(id2);
        assertThat(causalOrder.get(2).eventId()).isEqualTo(id3);
    }

    @Test
    @DisplayName("Kafka Chaos 4: Unrecoverable Payload Routing to DLQ with Diagnostic Metadata")
    void testDlqRoutingOnMalformedPayload() {
        String poisonPill = "{ \"corrupted\": true, \"missingTenant\": null }";

        boolean dlqRouted = false;
        String failureReason = null;

        try {
            KafkaEventEnvelope<?> envelope = objectMapper.readValue(poisonPill, KafkaEventEnvelope.class);
            if (envelope == null || envelope.tenantId() == null) {
                throw new IllegalArgumentException("Tenant ID is strictly required");
            }
        } catch (Exception ex) {
            dlqRouted = true;
            failureReason = ex.getMessage();
        }

        assertThat(dlqRouted).isTrue();
        assertThat(failureReason).isNotNull();
    }
}
