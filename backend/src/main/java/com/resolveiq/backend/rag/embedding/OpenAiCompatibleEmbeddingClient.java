package com.resolveiq.backend.rag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Production HTTP client for remote OpenAI-compatible embedding endpoints (OpenAI, Ollama, TEI).
 * Features retry with exponential backoff and jitter, rate-limit handling, and error logging.
 */
@Component
public class OpenAiCompatibleEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int dimension;
    private final int maxRetries;

    public OpenAiCompatibleEmbeddingClient(
            ObjectMapper objectMapper,
            @Value("${resolveiq.embedding.remote.url:https://api.openai.com/v1/embeddings}") String apiUrl,
            @Value("${resolveiq.embedding.remote.api-key:}") String apiKey,
            @Value("${resolveiq.embedding.remote.model:text-embedding-3-small}") String model,
            @Value("${resolveiq.embedding.remote.dimension:384}") int dimension,
            @Value("${resolveiq.embedding.remote.max-retries:3}") int maxRetries) {
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dimension = dimension;
        this.maxRetries = maxRetries;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public int getDimension() {
        return dimension;
    }

    public float[] embed(String text) {
        List<float[]> batch = embedBatch(List.of(text));
        return batch.isEmpty() ? new float[dimension] : batch.get(0);
    }

    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("input", texts);
        payload.put("model", model);
        payload.put("dimensions", dimension);

        int attempts = 0;
        long backoffMs = 200;
        Random random = new Random();

        while (attempts < maxRetries) {
            attempts++;
            try {
                String requestBody = objectMapper.writeValueAsString(payload);
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json");

                if (apiKey != null && !apiKey.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + apiKey);
                }

                HttpRequest request = reqBuilder.POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseResponse(response.body());
                } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    log.warn("Remote embedding API returned status {} on attempt {}/{}", response.statusCode(), attempts, maxRetries);
                    if (attempts >= maxRetries) {
                        throw new RuntimeException("Embedding API failed after " + maxRetries + " attempts with status: " + response.statusCode());
                    }
                    long jitter = random.nextInt(100);
                    Thread.sleep(backoffMs + jitter);
                    backoffMs *= 2;
                } else {
                    throw new RuntimeException("Embedding API rejected request with status: " + response.statusCode() + " body: " + response.body());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Embedding request interrupted", e);
            } catch (IOException e) {
                log.warn("Network exception calling embedding API on attempt {}/{}: {}", attempts, maxRetries, e.getMessage());
                if (attempts >= maxRetries) {
                    throw new RuntimeException("Embedding API network failure after " + maxRetries + " attempts", e);
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
                backoffMs *= 2;
            }
        }
        throw new RuntimeException("Embedding generation failed after " + maxRetries + " attempts");
    }

    private List<float[]> parseResponse(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new IOException("Unexpected response format from embedding API: missing 'data' array");
        }
        List<float[]> embeddings = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            JsonNode embeddingNode = item.path("embedding");
            if (embeddingNode.isArray()) {
                float[] vec = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    vec[i] = (float) embeddingNode.get(i).asDouble();
                }
                embeddings.add(vec);
            }
        }
        return embeddings;
    }
}
