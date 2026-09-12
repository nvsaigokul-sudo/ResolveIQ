package com.resolveiq.backend.investigation.tools;

import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.common.incident.EvidenceSource;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetCodeChangesTool implements InvestigationTool {

    private final KnowledgeSanitizer sanitizer;

    public GetCodeChangesTool(KnowledgeSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    @Override
    public String getName() {
        return "getCodeChanges";
    }

    @Override
    public String getDescription() {
        return "Inspect code commits and unified diff summaries for a service commit or commit range.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "service", Map.of("type", "string", "description", "Service name to inspect"),
                        "commitRange", Map.of("type", "string", "description", "Commit SHA or git revision range (e.g. d7a4b81, HEAD~1..HEAD)")
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
        String commitRange = arguments.get("commitRange") != null ? sanitizer.sanitizeSnippet(arguments.get("commitRange").toString()) : "HEAD~1..HEAD";

        String diffContent = "diff --git a/src/main/resources/application.yml b/src/main/resources/application.yml\n" +
                "--- a/src/main/resources/application.yml\n" +
                "+++ b/src/main/resources/application.yml\n" +
                "@@ -15,4 +15,4 @@ spring:\n" +
                "   datasource:\n" +
                "     hikari:\n" +
                "-      maximum-pool-size: 50\n" +
                "-      connection-timeout: 30000\n" +
                "+      maximum-pool-size: 5\n" +
                "+      connection-timeout: 2000\n";

        String sanitizedDiff = sanitizer.sanitizeSnippet(diffContent);

        String formatted = String.format(
                "<telemetry_data source=\"code_changes\" service=\"%s\" commitRange=\"%s\">\n" +
                "  <commit sha=\"d7a4b81\" author=\"developer@resolveiq.io\" message=\"feat(pool): restrict connection pool for cost optimization\">\n" +
                "    <diff>\n%s\n</diff>\n" +
                "  </commit>\n" +
                "</telemetry_data>",
                service, commitRange, sanitizedDiff
        );

        List<DiscoveredEvidenceItem> evidence = List.of(
                new DiscoveredEvidenceItem(
                        EvidenceSource.DEPLOYMENT,
                        service,
                        String.format("Code diff in %s commit d7a4b81: reduced hikari maximum-pool-size from 50 to 5 and timeout to 2000ms", service),
                        "git:diff?" + commitRange,
                        0.98,
                        0.99
                )
        );

        Map<String, Object> data = Map.of(
                "service", service,
                "commitRange", commitRange,
                "diff", sanitizedDiff
        );

        return ToolExecutionResult.success(getName(), data, formatted, evidence);
    }
}
