package com.resolveiq.backend.investigation.llm;

public record LlmToolCall(
        String id,
        String name,
        String argumentsJson
) {
}
