package com.resolveiq.backend.investigation.tools;

import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetServiceHealthTool implements InvestigationTool {

    private final KnowledgeSanitizer sanitizer;

    public GetServiceHealthTool(KnowledgeSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String getName() {
        return "getServiceHealth";
    }

    @Override
    public String getDescription() {
        return "Inspect current service operational health summary, uptime percentage, and active alerts.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "service", Map.of("type", "string", "description", "Service name to check")
                ),
                "required", List.of("service")
        );
    }

    @Override
    public ToolExecutionResult execute(UUID tenantId, UUID incidentId, Map<String, Object> arguments) {
        String rawService = (String) arguments.get("service");
        if (rawService == null || rawService.isBlank()) {
            return ToolExecutionResult.failure(getName(), "Missing required parameter 'service'");
        }
        String service = sanitizer.sanitizeSnippet(rawService);

        boolean isDegraded = "payment-service".equalsIgnoreCase(service) || "order-service".equalsIgnoreCase(service);
        double errorRate = isDegraded ? 8.4 : 0.02;
        int p95Latency = isDegraded ? 1750 : 45;
        String status = isDegraded ? "DEGRADED" : "HEALTHY";

        String formatted = String.format(
                "<telemetry_data source=\"service_health\" service=\"%s\" status=\"%s\">\n" +
                "  <metrics errorRate=\"%.2f%%\" p95LatencyMs=\"%d\" activePods=\"6/6\"/>\n" +
                "  <statusDescription>%s</statusDescription>\n" +
                "</telemetry_data>",
                service, status, errorRate, p95Latency,
                isDegraded ? "High 5xx error rate and connection pool saturation" : "Operating within normal SLO parameters"
        );

        Map<String, Object> data = Map.of(
                "service", service,
                "status", status,
                "errorRatePct", errorRate,
                "p95LatencyMs", p95Latency,
                "activePods", "6/6"
        );

        return ToolExecutionResult.success(getName(), data, formatted, Collections.emptyList());
    }
}
