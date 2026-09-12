package com.resolveiq.backend.investigation.llm;

import java.util.List;
import java.util.Map;

public record LlmRequest(
        String model,
        List<LlmMessage> messages,
        List<Map<String, Object>> tools,
        double temperature,
        int maxTokens
) {
}
