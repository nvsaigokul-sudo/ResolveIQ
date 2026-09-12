package com.resolveiq.backend.investigation.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Production HTTP client connecting to OpenAI-compatible LLM endpoints (PRD §22).
 * Features exponential backoff, jitter, 30s timeout, and structured tool-call serialization.
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(
            @Value("${resolveiq.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${resolveiq.llm.api-key:}") String apiKey,
            @Value("${resolveiq.llm.model:gpt-4o-mini}") String defaultModel,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getModelIdentifier() {
        return defaultModel;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenAI-compatible LLM client is not configured with an API key.");
        }

        String model = request.model() != null ? request.model() : defaultModel;
        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("model", model);
        bodyMap.put("temperature", request.temperature());
        if (request.maxTokens() > 0) {
            bodyMap.put("max_tokens", request.maxTokens());
        }

        List<Map<String, Object>> messageList = new ArrayList<>();
        for (LlmMessage msg : request.messages()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.role());
            if (msg.content() != null) {
                m.put("content", msg.content());
            }
            if (msg.name() != null) {
                m.put("name", msg.name());
            }
            if (msg.toolCallId() != null) {
                m.put("tool_call_id", msg.toolCallId());
            }
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                List<Map<String, Object>> tcList = new ArrayList<>();
                for (LlmToolCall tc : msg.toolCalls()) {
                    tcList.add(Map.of(
                            "id", tc.id(),
                            "type", "function",
                            "function", Map.of(
                                    "name", tc.name(),
                                    "arguments", tc.argumentsJson()
                            )
                    ));
                }
                m.put("tool_calls", tcList);
            }
            messageList.add(m);
        }
        bodyMap.put("messages", messageList);

        if (request.tools() != null && !request.tools().isEmpty()) {
            bodyMap.put("tools", request.tools());
        }

        int maxRetries = 3;
        long backoffMs = 500;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String requestJson = objectMapper.writeValueAsString(bodyMap);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .timeout(Duration.ofSeconds(30))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode choice = root.path("choices").path(0);
                    JsonNode messageNode = choice.path("message");
                    String content = messageNode.path("content").asText(null);
                    String finishReason = choice.path("finish_reason").asText("stop");

                    List<LlmToolCall> toolCalls = new ArrayList<>();
                    JsonNode toolCallsNode = messageNode.path("tool_calls");
                    if (toolCallsNode.isArray()) {
                        for (JsonNode tc : toolCallsNode) {
                            String id = tc.path("id").asText();
                            String name = tc.path("function").path("name").asText();
                            String args = tc.path("function").path("arguments").asText("{}");
                            toolCalls.add(new LlmToolCall(id, name, args));
                        }
                    }

                    int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
                    int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
                    int totalTokens = root.path("usage").path("total_tokens").asInt(promptTokens + completionTokens);

                    return new LlmResponse(content, toolCalls, promptTokens, completionTokens, totalTokens, finishReason);
                } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    log.warn("LLM API returned status {}, retrying attempt {}/{}", response.statusCode(), attempt, maxRetries);
                    Thread.sleep(backoffMs + (long) (Math.random() * 200));
                    backoffMs *= 2;
                } else {
                    throw new RuntimeException("LLM API request failed with status: " + response.statusCode() + " body: " + response.body());
                }
            } catch (Exception e) {
                lastError = e;
                log.warn("LLM request failed attempt {}/{}: {}", attempt, maxRetries, e.getMessage());
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ignored) {
                }
                backoffMs *= 2;
            }
        }

        throw new RuntimeException("LLM request exhausted retries", lastError);
    }
}
