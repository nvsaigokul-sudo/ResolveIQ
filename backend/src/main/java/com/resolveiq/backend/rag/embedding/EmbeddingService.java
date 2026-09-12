package com.resolveiq.backend.rag.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Primary embedding service managing provider selection, fallbacks, and error recovery.
 */
@Service
@Primary
public class EmbeddingService implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final DeterministicEmbeddingClient deterministicClient;
    private final OpenAiCompatibleEmbeddingClient remoteClient;
    private final String activeProvider;

    public EmbeddingService(
            DeterministicEmbeddingClient deterministicClient,
            OpenAiCompatibleEmbeddingClient remoteClient,
            @Value("${resolveiq.embedding.provider:deterministic}") String activeProvider) {
        this.deterministicClient = deterministicClient;
        this.remoteClient = remoteClient;
        this.activeProvider = activeProvider;
        log.info("Initialized EmbeddingService with active provider: {}", activeProvider);
    }

    @Override
    public int getDimension() {
        return "remote".equalsIgnoreCase(activeProvider) ? remoteClient.getDimension() : deterministicClient.getDimension();
    }

    @Override
    public float[] embed(String text) {
        if ("remote".equalsIgnoreCase(activeProvider)) {
            try {
                return remoteClient.embed(text);
            } catch (Exception e) {
                log.warn("Remote embedding failed, falling back to deterministic embedding: {}", e.getMessage());
                return deterministicClient.embed(text);
            }
        }
        return deterministicClient.embed(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if ("remote".equalsIgnoreCase(activeProvider)) {
            try {
                return remoteClient.embedBatch(texts);
            } catch (Exception e) {
                log.warn("Remote batch embedding failed, falling back to deterministic embedding: {}", e.getMessage());
                return deterministicClient.embedBatch(texts);
            }
        }
        return deterministicClient.embedBatch(texts);
    }
}
