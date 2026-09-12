package com.resolveiq.backend.investigation.llm;

import java.util.Collections;
import java.util.List;

public record LlmMessage(
        String role,
        String content,
        List<LlmToolCall> toolCalls,
        String toolCallId,
        String name
) {
    public static LlmMessage system(String content) {
        return new LlmMessage("system", content, Collections.emptyList(), null, null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage("user", content, Collections.emptyList(), null, null);
    }

    public static LlmMessage assistant(String content, List<LlmToolCall> toolCalls) {
        return new LlmMessage("assistant", content, toolCalls != null ? toolCalls : Collections.emptyList(), null, null);
    }

    public static LlmMessage toolResponse(String toolCallId, String toolName, String content) {
        return new LlmMessage("tool", content, Collections.emptyList(), toolCallId, toolName);
    }
}
