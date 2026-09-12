package com.resolveiq.backend.investigation.llm;

import java.util.Collections;
import java.util.List;

public record LlmResponse(
        String content,
        List<LlmToolCall> toolCalls,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String finishReason
) {
    public static LlmResponse message(String content, int promptTokens, int completionTokens) {
        return new LlmResponse(content, Collections.emptyList(), promptTokens, completionTokens,
                promptTokens + completionTokens, "stop");
    }

    public static LlmResponse toolCalls(List<LlmToolCall> calls, int promptTokens, int completionTokens) {
        return new LlmResponse(null, calls != null ? calls : Collections.emptyList(), promptTokens, completionTokens,
                promptTokens + completionTokens, "tool_calls");
    }
}
