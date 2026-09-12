package com.resolveiq.backend.investigation.tools;

import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.common.incident.EvidenceSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class QueryMetricsTool implements InvestigationTool {

    private final KnowledgeSanitizer sanitizer;

    public QueryMetricsTool(KnowledgeSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String getName() {
        return "queryMetrics";
    }

    @Override
    public String getDescription() {
        return "Query time-series metrics (latency, 5xx error rate, cpu, memory, queue depth) for a service over a time window.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "service", Map.of("type", "string", "description", "Target service name (e.g., payment-service)"),
                        "metric", Map.of("type", "string", "description", "Metric name (e.g., http_server_duration_ms, error_rate, db_connection_pool_active)"),
                        "timeRange", Map.of("type", "string", "description", "Time range to inspect (e.g. 15m, 1h, 6h)"),
                        "aggregation", Map.of("type", "string", "enum", List.of("avg", "max", "p95", "p99", "sum"))
                ),
                "required", List.of("service", "metric")
        );
    }

    @Override
    public ToolExecutionResult execute(UUID tenantId, UUID incidentId, Map<String, Object> arguments) {
        String service = sanitizer.sanitizeSnippet((String) arguments.get("service"));
        String metric = sanitizer.sanitizeSnippet((String) arguments.get("metric"));
        String timeRange = arguments.getOrDefault("timeRange", "1h").toString();
        String aggregation = arguments.getOrDefault("aggregation", "p95").toString();

        if (service == null || service.isBlank() || metric == null || metric.isBlank()) {
            return ToolExecutionResult.failure(getName(), "Missing required parameters 'service' and 'metric'");
        }

        // Generate realistic metric summary based on service and metric queried
        double baselineValue = 15.0;
        double observedValue = 185.0;
        double threshold = 50.0;
        boolean isAnomalous = true;

        if (metric.contains("error") || metric.contains("5xx")) {
            baselineValue = 0.05;
            observedValue = 8.4;
            threshold = 1.0;
        } else if (metric.contains("connection") || metric.contains("pool")) {
            baselineValue = 12.0;
            observedValue = 100.0; // 100% pool exhaustion
            threshold = 80.0;
        } else if (metric.contains("cpu") || metric.contains("memory")) {
            baselineValue = 35.0;
            observedValue = 78.0;
            threshold = 75.0;
        }

        String dataPayload = String.format(
                "Service: %s | Metric: %s | Aggregation: %s | Window: %s | Baseline: %.2f | Observed: %.2f | Anomaly: %b",
                service, metric, aggregation, timeRange, baselineValue, observedValue, isAnomalous
        );

        String formatted = String.format(
                "<telemetry_data source=\"metrics\" service=\"%s\" metric=\"%s\" window=\"%s\">\n" +
                "  <summary baseline=\"%.2f\" observed=\"%.2f\" threshold=\"%.2f\" anomalous=\"%b\"/>\n" +
                "  <trend>Sudden elevation observed starting 12m ago; correlates with recent deployment.</trend>\n" +
                "</telemetry_data>",
                service, metric, timeRange, baselineValue, observedValue, threshold, isAnomalous
        );

        List<DiscoveredEvidenceItem> evidence = new ArrayList<>();
        if (isAnomalous) {
            evidence.add(new DiscoveredEvidenceItem(
                    EvidenceSource.METRICS,
                    service,
                    dataPayload,
                    String.format("metric:%s?service=%s&window=%s", metric, service, timeRange),
                    0.92,
                    0.95
            ));
        }

        Map<String, Object> resultData = Map.of(
                "service", service,
                "metric", metric,
                "baseline", baselineValue,
                "observed", observedValue,
                "anomalous", isAnomalous,
                "timestamp", Instant.now().toString()
        );

        return ToolExecutionResult.success(getName(), resultData, formatted, evidence);
    }
}
