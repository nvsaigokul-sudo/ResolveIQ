package com.resolveiq.backend.rag.embedding;

import java.util.List;

/**
 * Contract for dense vector embedding generation per PRD §22, §25, §36.1.
 */
public interface EmbeddingClient {

    /**
     * Generate a dense vector embedding for a single text chunk.
     */
    float[] embed(String text);

    /**
     * Generate embeddings for a batch of text chunks.
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * Vector dimensionality (e.g. 384).
     */
    int getDimension();
}
