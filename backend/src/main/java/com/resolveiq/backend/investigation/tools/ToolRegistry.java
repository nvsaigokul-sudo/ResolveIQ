package com.resolveiq.backend.investigation.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * Registry and dispatcher for the 11 read-only investigation tools (PRD §25).
 * Enforces TenantContext, strict parameter validation, 10s execution timeout, and result bounding.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final int MAX_RESULT_CHARS = 8000;
    private static final long TOOL_TIMEOUT_SECONDS = 10;

    private final Map<String, InvestigationTool> tools = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ToolRegistry(List<InvestigationTool> toolList) {
        for (InvestigationTool tool : toolList) {
            tools.put(tool.getName(), tool);
            log.info("Registered investigation tool: {}", tool.getName());
        }
    }

    public Optional<InvestigationTool> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Set<String> getRegisteredToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    public List<Map<String, Object>> getOpenAiToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (InvestigationTool tool : tools.values()) {
            definitions.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.getName(),
                            "description", tool.getDescription(),
                            "parameters", tool.getParameterSchema()
                    )
            ));
        }
        return definitions;
    }

    public ToolExecutionResult execute(UUID tenantId, UUID incidentId, String toolName, Map<String, Object> arguments) {
        if (tenantId == null) {
            return ToolExecutionResult.failure(toolName, "Missing tenant context. Investigation tools require valid tenant scope.");
        }

        InvestigationTool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("Attempt to execute unregistered tool: {}", toolName);
            return ToolExecutionResult.failure(toolName, String.format("Tool '%s' is not registered. Available tools: %s",
                    toolName, String.join(", ", tools.keySet())));
        }

        Map<String, Object> safeArgs = arguments != null ? arguments : Collections.emptyMap();

        // Enforce 10-second per-tool timeout budget
        Future<ToolExecutionResult> future = executor.submit(() -> tool.execute(tenantId, incidentId, safeArgs));
        try {
            ToolExecutionResult result = future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Enforce response character budget
            if (result.formattedResult() != null && result.formattedResult().length() > MAX_RESULT_CHARS) {
                String truncated = result.formattedResult().substring(0, MAX_RESULT_CHARS) + "\n<!-- TRUNCATED TO BUDGET -->";
                return new ToolExecutionResult(result.success(), result.toolName(), result.data(), truncated,
                        result.discoveredEvidence(), result.errorMessage());
            }
            return result;
        } catch (TimeoutException te) {
            future.cancel(true);
            log.warn("Tool {} timed out after {}s", toolName, TOOL_TIMEOUT_SECONDS);
            return ToolExecutionResult.failure(toolName, String.format("Tool execution exceeded %ds timeout", TOOL_TIMEOUT_SECONDS));
        } catch (Exception e) {
            log.warn("Tool {} failed with error: {}", toolName, e.getMessage());
            return ToolExecutionResult.failure(toolName, "Tool execution error: " + e.getMessage());
        }
    }
}
