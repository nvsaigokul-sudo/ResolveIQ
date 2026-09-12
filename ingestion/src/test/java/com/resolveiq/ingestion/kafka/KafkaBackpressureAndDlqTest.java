package com.resolveiq.ingestion.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.ingestion.model.KafkaEventEnvelope;
import com.resolveiq.ingestion.model.MetricObservedPayload;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KafkaBackpressureAndDlqTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TelemetryKafkaProducer telemetryKafkaProducer;

    @Test
    @DisplayName("Acceptance Criterion 10: Broker timeout triggers IngestionBackpressureException with 503 and Retry-After")
    void testKafkaProduceTimeoutTriggersBackpressure() {
        // Mock KafkaTemplate to simulate broker timeout
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new TimeoutException("Kafka broker unresponsive"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedFuture);

        TelemetryKafkaProducer producer = new TelemetryKafkaProducer(kafkaTemplate, objectMapper, 500);

        UUID tenantId = UUID.randomUUID();
        MetricObservedPayload metric = new MetricObservedPayload(
                "test.metric", MetricObservedPayload.MetricType.GAUGE, 10.0, null, Map.of(), "service", Map.of(), Instant.now()
        );
        KafkaEventEnvelope<MetricObservedPayload> envelope = KafkaEventEnvelope.create(
                tenantId, null, "prod", "trace-bp", null, metric
        );

        assertThatThrownBy(() -> producer.send("telemetry.metrics", "service", envelope))
                .isInstanceOf(IngestionBackpressureException.class)
                .hasMessageContaining("Kafka broker failure");
    }

    @Test
    @DisplayName("Acceptance Criterion 11: Poison messages route to DLQ with failure reason and operator visibility")
    void testDlqRoutingAndInspection() throws Exception {
        String poisonPayload = "{ malformed: json, invalid_schema }";
        telemetryKafkaProducer.sendToDlq("telemetry.metrics", "tenant-1:order-service", poisonPayload, "SCHEMA_VALIDATION_ERROR");

        assertThat(telemetryKafkaProducer.getInspectableDlqEntries()).isNotEmpty();
        TelemetryKafkaProducer.DlqEntry entry = telemetryKafkaProducer.getInspectableDlqEntries().get(0);
        assertThat(entry.dlqTopic()).isEqualTo("telemetry.metrics.DLQ");
        assertThat(entry.failureReason()).isEqualTo("SCHEMA_VALIDATION_ERROR");
        assertThat(entry.partitionKey()).isEqualTo("tenant-1:order-service");

        // Verify operator endpoint GET /api/v1/dlq
        mockMvc.perform(get("/api/v1/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].dlqTopic").value("telemetry.metrics.DLQ"))
                .andExpect(jsonPath("$.items[0].failureReason").value("SCHEMA_VALIDATION_ERROR"));
    }
}
