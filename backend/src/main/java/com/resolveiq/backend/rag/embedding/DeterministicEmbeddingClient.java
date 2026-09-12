package com.resolveiq.backend.rag.embedding;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Deterministic semantic embedding client producing 384-dimensional normalized vectors.
 * Combines domain-semantic subspace projection with subword n-gram feature hashing.
 * Guarantees 100% reproducibility across nodes and tests without external API dependencies.
 */
@Component
public class DeterministicEmbeddingClient implements EmbeddingClient {

    public static final int DIMENSION = 384;

    // Domain semantic clusters for incident and SRE terminology
    private static final Map<String, Integer> DOMAIN_BASINS = new HashMap<>();

    static {
        // Database & Storage basin: dimensions 0-63
        registerBasin(0, "database", "db", "postgres", "postgresql", "mysql", "redis", "connection",
                "pool", "hikari", "query", "deadlock", "lock", "index", "table", "sql", "hibernate", "transaction");

        // Network, Gateway & HTTP basin: dimensions 64-127
        registerBasin(64, "gateway", "ingress", "proxy", "route", "http", "status", "5xx", "502",
                "503", "504", "timeout", "latency", "socket", "dns", "tls", "reset", "upstream", "downstream");

        // Compute, System & Resource basin: dimensions 128-191
        registerBasin(128, "cpu", "memory", "ram", "oom", "heap", "leak", "gc", "thread",
                "saturation", "disk", "iops", "pod", "restart", "crashloop", "capacity", "container");

        // Deployment, Configuration & Change basin: dimensions 192-255
        registerBasin(192, "deployment", "release", "rollback", "version", "v2", "canary", "deploy",
                "commit", "merge", "configuration", "config", "env", "flag", "pipeline", "build");

        // Incident, Alert & Outage basin: dimensions 256-319
        registerBasin(256, "outage", "degradation", "failure", "incident", "critical", "sev1", "sev2",
                "error", "exception", "alert", "breach", "anomaly", "down", "unresponsive", "broken");

        // Runbook, Operations & Mitigation basin: dimensions 320-383
        registerBasin(320, "runbook", "restart", "scale", "failover", "drain", "mitigation",
                "verify", "check", "health", "ping", "status", "action", "remediation", "playbook");
    }

    private static void registerBasin(int startOffset, String... keywords) {
        for (String kw : keywords) {
            DOMAIN_BASINS.put(kw.toLowerCase(Locale.ROOT), startOffset);
        }
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSION];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s_-]", " ");
        String[] words = normalized.split("\\s+");

        for (String word : words) {
            if (word.isBlank() || word.length() < 2) continue;

            // 1. Check domain basin
            Integer basinOffset = DOMAIN_BASINS.get(word);
            if (basinOffset != null) {
                for (int i = 0; i < 64; i++) {
                    int dim = (basinOffset + i) % DIMENSION;
                    vector[dim] += 1.5f * (float) Math.cos((i * 13) % 17);
                }
            }

            // 2. Word hash projection
            int wordHash = hashString(word);
            for (int k = 0; k < 4; k++) {
                int dim = Math.abs((wordHash ^ (k * 7919))) % DIMENSION;
                float sign = ((wordHash >> k) & 1) == 0 ? 1.0f : -1.0f;
                vector[dim] += sign * 1.0f;
            }

            // 3. Subword character n-grams (3-grams)
            if (word.length() >= 3) {
                for (int i = 0; i <= word.length() - 3; i++) {
                    String trigram = word.substring(i, i + 3);
                    int triHash = hashString(trigram);
                    int dim = Math.abs(triHash) % DIMENSION;
                    float sign = (triHash & 1) == 0 ? 0.4f : -0.4f;
                    vector[dim] += sign;
                }
            }
        }

        // 4. L2 Normalization to unit length
        normalizeL2(vector);
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null) return List.of();
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    private static void normalizeL2(float[] vector) {
        double sumSq = 0.0;
        for (float v : vector) {
            sumSq += v * v;
        }
        if (sumSq > 1e-12) {
            float norm = (float) Math.sqrt(sumSq);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    private static int hashString(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            return s.hashCode();
        }
    }
}
