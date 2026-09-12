package com.resolveiq.backend.investigation.tools;

import com.resolveiq.backend.domain.IncidentEventEntity;
import com.resolveiq.backend.rag.security.KnowledgeSanitizer;
import com.resolveiq.backend.repository.IncidentEventRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetIncidentTimelineTool implements InvestigationTool {

    private final IncidentEventRepository incidentEventRepository;
    private final KnowledgeSanitizer sanitizer;

    public GetIncidentTimelineTool(IncidentEventRepository incidentEventRepository,
                                  KnowledgeSanitizer sanitizer) {
        this.incidentEventRepository = incidentEventRepository;
        this.sanitizer = sanitizer;
    }

    @Override
    public String getName() {
        return "getIncidentTimeline";
    }

    @Override
    public String getDescription() {
        return "Inspect the append-only chronological event timeline of the incident.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "incidentId", Map.of("type", "string", "description", "Incident ID (defaults to current incident if omitted)")
                )
        );
    }

    @Override
    public ToolExecutionResult execute(UUID tenantId, UUID incidentId, Map<String, Object> arguments) {
        UUID targetIncidentId = incidentId;
        if (arguments != null && arguments.get("incidentId") != null) {
            try {
                targetIncidentId = UUID.fromString(arguments.get("incidentId").toString());
            } catch (Exception ignored) {
            }
        }

        List<IncidentEventEntity> events = incidentEventRepository
                .findByIncidentIdAndTenantIdOrderByCreatedAtAsc(targetIncidentId, tenantId);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<telemetry_data source=\"incident_timeline\" incidentId=\"%s\" eventCount=\"%d\">\n",
                targetIncidentId, events.size()));

        List<Map<String, String>> eventList = new ArrayList<>();
        for (IncidentEventEntity e : events) {
            String sanitizedSummary = sanitizer.sanitizeSnippet(e.getSummary());
            sb.append(String.format("  <event type=\"%s\" timestamp=\"%s\" actor=\"%s\">%s</event>\n",
                    e.getEventType(), e.getCreatedAt(), e.getActorType(), sanitizedSummary));
            eventList.add(Map.of(
                    "id", e.getId().toString(),
                    "type", e.getEventType().name(),
                    "timestamp", e.getCreatedAt().toString(),
                    "summary", sanitizedSummary
            ));
        }
        sb.append("</telemetry_data>");

        return ToolExecutionResult.success(getName(), eventList, sb.toString(), Collections.emptyList());
    }
}
