package com.resolveiq.ingestion.controller;

import com.resolveiq.common.dto.ApiResponse;
import com.resolveiq.ingestion.kafka.TelemetryKafkaProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Operator inspection endpoint for Dead-Letter Queues (PRD §15.1).
 */
@RestController
@RequestMapping("/api/v1/dlq")
public class DlqInspectionController {

    private final TelemetryKafkaProducer kafkaProducer;

    public DlqInspectionController(TelemetryKafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TelemetryKafkaProducer.DlqEntry>> inspectDlq() {
        List<TelemetryKafkaProducer.DlqEntry> entries = kafkaProducer.getInspectableDlqEntries();
        return ResponseEntity.ok(ApiResponse.ofItems(entries, null, (long) entries.size()));
    }
}
