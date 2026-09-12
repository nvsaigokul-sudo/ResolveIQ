package com.resolveiq.backend.rag.retrieval;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Calculates cosine vector similarity and normalizes scores per PRD §22, §25.
 */
@Component
public class VectorSimilarityRanker {

    /**
     * Computes cosine similarity between query vector and candidate item vectors.
     */
    public <T> Map<T, Double> score(float[] queryVector, List<T> items, java.util.function.Function<T, float[]> vectorExtractor) {
        if (queryVector == null || queryVector.length == 0 || items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<T, Double> scores = new HashMap<>();
        for (T item : items) {
            float[] itemVector = vectorExtractor.apply(item);
            double sim = cosineSimilarity(queryVector, itemVector);
            scores.put(item, sim);
        }

        return scores;
    }

    public static double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length == 0 || v2.length == 0 || v1.length != v2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }

        if (norm1 < 1e-12 || norm2 < 1e-12) {
            return 0.0;
        }

        double raw = dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
        // Clamp to [0.0, 1.0]
        return Math.max(0.0, Math.min(1.0, raw));
    }
}
