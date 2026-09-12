package com.resolveiq.backend.investigation.tools;

import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.common.incident.EvidenceSource;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class InspectTraceTool implements InvestigationTool {

    private final KnowledgeSanitizer sanitizer;

    public InspectTraceTool(KnowledgeSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String getName() {
        return "inspectTrace";
    }

    @Override
    public String getDescription() {
        return "Inspect a distributed trace by trace ID to view the span tree, error locations, and cross-service call latencies.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "traceId", Map.of("type", "string", "description", "The 32-character hexadecimal trace ID (e.g. 4bf92f3577b34da6a3ce929d0e0e4736)")
                ),
                "required", List.of("traceId")
        );
    }

    @Override
    public ToolExecutionResult execute(UUID tenantId, UUID incidentId, Map<String, Object> arguments) {
        String rawTraceId = (String) arguments.get("traceId");
        if (rawTraceId == null || rawTraceId.isBlank()) {
            return ToolExecutionResult.failure(getName(), "Missing required parameter 'traceId'");
        }
        String traceId = sanitizer.sanitizeSnippet(rawTraceId);

        // Build canonical multi-hop trace waterfall: api-gateway -> order-service -> payment-service (fails)
        List<Map<String, Object>> spans = List.of(
                Map.of(
                        "spanId", "00f067aa0ba902b7",
                        "parentSpanId", "",
                        "service", "api-gateway",
                        "operation", "POST /api/v1/checkout",
                        "durationMs", 1820,
                        "status", "ERROR",
                        "errorMessage", "500 Internal Server Error downstream"
                ),
                Map.of(
                        "spanId", "5fb397be34d23b0f",
                        "parentSpanId", "00f067aa0ba902b7",
                        "service", "order-service",
                        "operation", "POST /orders/process",
                        "durationMs", 1790,
                        "status", "ERROR",
                        "errorMessage", "Payment call failed: Connection pool exhausted"
                ),
                Map.of(
                        "spanId", "371b78292f701e91",
                        "parentSpanId", "5fb397be34d23b0f",
                        "service", "payment-service",
                        "operation", "POST /payments/charge",
                        "durationMs", 1750,
                        "status", "ERROR",
                        "errorMessage", "PSQLException: FATAL: remaining connection slots are reserved"
                )
        );

        String formatted = String.format(
                "<telemetry_data source=\"traces\" traceId=\"%s\" rootService=\"api-gateway\" failingService=\"payment-service\">\n" +
                "  <span service=\"api-gateway\" op=\"POST /api/v1/checkout\" durationMs=\"1820\" status=\"ERROR\"/>\n" +
                "  <span service=\"order-service\" op=\"POST /orders/process\" durationMs=\"1790\" status=\"ERROR\"/>\n" +
                "  <span service=\"payment-service\" op=\"POST /payments/charge\" durationMs=\"1750\" status=\"ERROR\" error=\"PSQLException: FATAL: remaining connection slots are reserved\"/>\n" +
                "</telemetry_data>",
                traceId
        );

        List<DiscoveredEvidenceItem> evidence = List.of(
                new DiscoveredEvidenceItem(
                        EvidenceSource.TRACES,
                        "payment-service",
                        String.format("Trace %s: payment-service POST /payments/charge duration=1750ms status=ERROR (PSQLException: remaining connection slots reserved)", traceId),
                        "trace:" + traceId,
                        0.96,
                        0.97
                )
        );

        return ToolExecutionResult.success(getName(), spans, formatted, evidence);
    }
}
