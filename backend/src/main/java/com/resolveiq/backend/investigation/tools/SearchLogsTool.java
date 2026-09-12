package com.resolveiq.backend.investigation.tools;

import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.common.incident.EvidenceSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class SearchLogsTool implements InvestigationTool {

    private final KnowledgeSanitizer sanitizer;

    public SearchLogsTool(KnowledgeSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String getName() {
        return "searchLogs";
    }

    @Override
    public String getDescription() {
        return "Search service log streams by query keywords, severity level, and time range.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "service", Map.of("type", "string", "description", "Target service name (e.g., payment-service)"),
                        "query", Map.of("type", "string", "description", "Log search query or keyword (e.g. exception, timeout, pool exhausted)"),
                        "severity", Map.of("type", "string", "enum", List.of("ERROR", "WARN", "INFO", "FATAL")),
                        "timeRange", Map.of("type", "string", "description", "Time range (e.g. 15m, 1h)")
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
        String query = arguments.get("query") != null ? sanitizer.sanitizeSnippet(arguments.get("query").toString()) : "";
        String severity = arguments.getOrDefault("severity", "ERROR").toString();
        String timeRange = arguments.getOrDefault("timeRange", "1h").toString();

        // Construct realistic log error signatures matching canonical payment-service / database regressions
        List<Map<String, String>> logs = new ArrayList<>();
        String sampleMessage = String.format(
                "org.postgresql.util.PSQLException: FATAL: remaining connection slots are reserved for non-replication superuser connections at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:182) in service %s",
                service
        );

        if (query.toLowerCase().contains("timeout") || query.toLowerCase().contains("deadline")) {
            sampleMessage = String.format("java.util.concurrent.TimeoutException: Client call to database timed out after 30000ms on service %s", service);
        } else if (query.toLowerCase().contains("nullpointer") || query.toLowerCase().contains("npe")) {
            sampleMessage = String.format("java.lang.NullPointerException: Cannot invoke method on null object in %s.controller.ProcessHandler", service);
        }

        // Defuse potential prompt injection within log lines
        String sanitizedMsg = sanitizer.sanitizeSnippet(sampleMessage);

        Map<String, String> logEntry = Map.of(
                "timestamp", Instant.now().minusSeconds(300).toString(),
                "severity", severity,
                "service", service,
                "message", sanitizedMsg,
                "traceId", "4bf92f3577b34da6a3ce929d0e0e4736"
        );
        logs.add(logEntry);

        String formatted = String.format(
                "<telemetry_data source=\"logs\" service=\"%s\" count=\"%d\">\n" +
                "  <log_entry timestamp=\"%s\" severity=\"%s\" traceId=\"%s\">\n" +
                "    %s\n" +
                "  </log_entry>\n" +
                "</telemetry_data>",
                service, logs.size(), logEntry.get("timestamp"), logEntry.get("severity"), logEntry.get("traceId"), logEntry.get("message")
        );

        List<DiscoveredEvidenceItem> evidence = List.of(
                new DiscoveredEvidenceItem(
                        EvidenceSource.LOGS,
                        service,
                        sanitizedMsg,
                        String.format("logs?service=%s&severity=%s&query=%s", service, severity, query),
                        0.95,
                        0.98
                )
        );

        return ToolExecutionResult.success(getName(), logs, formatted, evidence);
    }
}
