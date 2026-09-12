package com.resolveiq.backend.investigation.tools;

import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.common.incident.EvidenceSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class GetConfigurationChangesTool implements InvestigationTool {

    private final KnowledgeSanitizer sanitizer;

    public GetConfigurationChangesTool(KnowledgeSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String getName() {
        return "getConfigurationChanges";
    }

    @Override
    public String getDescription() {
        return "Inspect recent configuration changes, environment variable updates, and feature flag toggles for a service.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "service", Map.of("type", "string", "description", "Service name to inspect"),
                        "timeRange", Map.of("type", "string", "description", "Time range (e.g. 1h, 2h, 24h)")
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

        List<Map<String, String>> diffs = new ArrayList<>();
        if ("payment-service".equalsIgnoreCase(service)) {
            diffs.add(Map.of(
                    "key", "spring.datasource.hikari.maximum-pool-size",
                    "previousValue", "50",
                    "newValue", "5",
                    "modifiedBy", "ci-pipeline",
                    "timestamp", Instant.now().minusSeconds(840).toString()
            ));
            diffs.add(Map.of(
                    "key", "spring.datasource.hikari.connection-timeout",
                    "previousValue", "30000",
                    "newValue", "2000",
                    "modifiedBy", "ci-pipeline",
                    "timestamp", Instant.now().minusSeconds(840).toString()
            ));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<telemetry_data source=\"config_changes\" service=\"%s\" count=\"%d\">\n", service, diffs.size()));
        for (Map<String, String> diff : diffs) {
            sb.append(String.format("  <change key=\"%s\" prev=\"%s\" new=\"%s\" by=\"%s\" time=\"%s\"/>\n",
                    diff.get("key"), diff.get("previousValue"), diff.get("newValue"), diff.get("modifiedBy"), diff.get("timestamp")));
        }
        sb.append("</telemetry_data>");

        List<DiscoveredEvidenceItem> evidence = new ArrayList<>();
        if (!diffs.isEmpty()) {
            evidence.add(new DiscoveredEvidenceItem(
                    EvidenceSource.CONFIG,
                    service,
                    String.format("Configuration change on %s: maximum-pool-size reduced from 50 to 5, connection-timeout reduced from 30000ms to 2000ms", service),
                    "config:" + service,
                    0.97,
                    0.99
            ));
        }

        return ToolExecutionResult.success(getName(), diffs, sb.toString(), evidence);
    }
}
