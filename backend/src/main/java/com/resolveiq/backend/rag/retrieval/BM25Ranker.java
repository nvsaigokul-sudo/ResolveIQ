package com.resolveiq.backend.rag.retrieval;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Deterministic Okapi BM25 ranker per PRD §22, §25.
 * Parameters: k1 = 1.2, b = 0.75.
 */
@Component
public class BM25Ranker {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "else", "when",
            "at", "by", "for", "with", "about", "against", "between", "into", "through",
            "during", "before", "after", "above", "below", "to", "from", "up", "down",
            "in", "out", "on", "off", "over", "under", "again", "further", "once",
            "here", "there", "all", "any", "both", "each", "few", "more", "most", "other",
            "some", "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too",
            "very", "s", "t", "can", "will", "just", "don", "should", "now", "is", "are",
            "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does", "did"
    );

    public static class ScoredItem<T> {
        private final T item;
        private final double score;

        public ScoredItem(T item, double score) {
            this.item = item;
            this.score = score;
        }

        public T getItem() {
            return item;
        }

        public double getScore() {
            return score;
        }
    }

    /**
     * Scores documents against a query using Okapi BM25 and normalizes scores to [0.0, 1.0].
     */
    public <T> Map<T, Double> score(String query, List<T> items, java.util.function.Function<T, String> textExtractor) {
        if (query == null || query.isBlank() || items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyMap();
        }

        int N = items.size();
        List<List<String>> tokenizedDocs = new ArrayList<>(N);
        double totalLength = 0;

        for (T item : items) {
            List<String> tokens = tokenize(textExtractor.apply(item));
            tokenizedDocs.add(tokens);
            totalLength += tokens.size();
        }

        double avgDocLength = (N > 0) ? (totalLength / N) : 1.0;

        // Calculate Document Frequency for each query term
        Map<String, Integer> docFrequency = new HashMap<>();
        for (String qTerm : queryTokens) {
            int df = 0;
            for (List<String> docTokens : tokenizedDocs) {
                if (docTokens.contains(qTerm)) {
                    df++;
                }
            }
            docFrequency.put(qTerm, df);
        }

        // Calculate BM25 score for each document
        Map<T, Double> rawScores = new HashMap<>();
        double maxScore = 0.0;

        for (int i = 0; i < N; i++) {
            T item = items.get(i);
            List<String> docTokens = tokenizedDocs.get(i);
            int docLen = docTokens.size();

            // Count term frequencies in this document
            Map<String, Integer> termFreqs = new HashMap<>();
            for (String t : docTokens) {
                termFreqs.put(t, termFreqs.getOrDefault(t, 0) + 1);
            }

            double score = 0.0;
            for (String qTerm : queryTokens) {
                int tf = termFreqs.getOrDefault(qTerm, 0);
                if (tf == 0) continue;

                int df = docFrequency.getOrDefault(qTerm, 0);
                // Robertson-Spärck Jones IDF
                double idf = Math.log(1.0 + (N - df + 0.5) / (df + 0.5));
                if (idf < 0) idf = 0.01;

                double numerator = tf * (K1 + 1.0);
                double denominator = tf + K1 * (1.0 - B + B * (docLen / avgDocLength));
                score += idf * (numerator / denominator);
            }

            rawScores.put(item, score);
            if (score > maxScore) {
                maxScore = score;
            }
        }

        // Normalize scores to [0.0, 1.0]
        Map<T, Double> normalizedScores = new HashMap<>();
        for (Map.Entry<T, Double> entry : rawScores.entrySet()) {
            double normalized = (maxScore > 1e-9) ? (entry.getValue() / maxScore) : 0.0;
            normalizedScores.put(entry.getKey(), normalized);
        }

        return normalizedScores;
    }

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-\\.]", " ");
        String[] parts = clean.split("\\s+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.length() >= 2 && !STOP_WORDS.contains(trimmed)) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }
}
