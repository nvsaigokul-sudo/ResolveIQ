package com.resolveiq.backend.investigation.tools;

import java.util.Map;
import java.util.UUID;

/**
 * Contract for strongly-typed, read-only investigation tools (PRD §25).
 */
public interface InvestigationTool {

    String getName();

    String getDescription();

    Map<String, Object> getParameterSchema();

    ToolExecutionResult execute(UUID tenantId, UUID incidentId, Map<String, Object> arguments);
}
