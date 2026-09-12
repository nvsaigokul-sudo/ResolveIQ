package com.resolveiq.backend.rag;

import com.resolveiq.backend.rag.embedding.DeterministicEmbeddingClient;
import com.resolveiq.backend.rag.embedding.EmbeddingService;
import com.resolveiq.backend.rag.embedding.OpenAiCompatibleEmbeddingClient;
import com.resolveiq.backend.rag.retrieval.VectorSimilarityRanker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingServiceTest {

    private EmbeddingService embeddingService;
    private DeterministicEmbeddingClient deterministicClient;

    @BeforeEach
    void setUp() {
        deterministicClient = new DeterministicEmbeddingClient();
        OpenAiCompatibleEmbeddingClient remoteClient = Mockito.mock(OpenAiCompatibleEmbeddingClient.class);
        embeddingService = new EmbeddingService(deterministicClient, remoteClient, "deterministic");
    }

    @Test
    @DisplayName("Generates exact 384-dimensional normalized vectors deterministically")
    void testDeterministicReproducibilityAndNormalization() {
        String text = "Database connection pool timeout in payment-service caused by HikariCP exhaustion";

        float[] v1 = embeddingService.embed(text);
        float[] v2 = embeddingService.embed(text);

        assertThat(v1).hasSize(384);
        assertThat(v2).hasSize(384);

        // 100% bitwise reproducible for identical text
        for (int i = 0; i < 384; i++) {
            assertThat(v1[i]).isEqualTo(v2[i]);
        }

        // L2 norm must be 1.0 (unit vector)
        double normSq = 0.0;
        for (float v : v1) {
            normSq += v * v;
        }
        assertThat(Math.sqrt(normSq)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("Demonstrates semantic proximity: related texts have high cosine, unrelated texts have low cosine")
    void testSemanticProximity() {
        String textDb1 = "PostgreSQL database connection pool exhausted HikariCP timeout";
        String textDb2 = "Database query failure due to db pool connection deadlock";
        String textFrontend = "Frontend CSS styled button react component layout";

        float[] vDb1 = embeddingService.embed(textDb1);
        float[] vDb2 = embeddingService.embed(textDb2);
        float[] vFrontend = embeddingService.embed(textFrontend);

        double simRelated = VectorSimilarityRanker.cosineSimilarity(vDb1, vDb2);
        double simUnrelated = VectorSimilarityRanker.cosineSimilarity(vDb1, vFrontend);

        assertThat(simRelated).isGreaterThan(0.60);
        assertThat(simUnrelated).isLessThan(0.35);
        assertThat(simRelated).isGreaterThan(simUnrelated);
    }

    @Test
    @DisplayName("Handles empty and blank strings gracefully without throwing exceptions")
    void testEmptyAndBlankStrings() {
        float[] emptyVec = embeddingService.embed("");
        assertThat(emptyVec).hasSize(384);

        float[] nullVec = embeddingService.embed(null);
        assertThat(nullVec).hasSize(384);

        float[] whitespaceVec = embeddingService.embed("   \n\t  ");
        assertThat(whitespaceVec).hasSize(384);
    }

    @Test
    @DisplayName("Batch embedding matches individual embedding results")
    void testBatchEmbeddingMatchesIndividual() {
        List<String> texts = List.of(
                "Payment service timeout",
                "Order service 503 gateway error",
                "Postgres deadlocks"
        );

        List<float[]> batch = embeddingService.embedBatch(texts);
        assertThat(batch).hasSize(3);

        for (int i = 0; i < texts.size(); i++) {
            float[] individual = embeddingService.embed(texts.get(i));
            assertThat(batch.get(i)).containsExactly(individual);
        }
    }
}
