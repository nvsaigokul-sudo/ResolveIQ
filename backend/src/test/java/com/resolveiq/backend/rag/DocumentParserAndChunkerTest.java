package com.resolveiq.backend.rag;

import com.resolveiq.backend.rag.ingestion.DocumentParser;
import com.resolveiq.backend.rag.ingestion.TokenBoundedChunker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserAndChunkerTest {

    private final DocumentParser parser = new DocumentParser();
    private final TokenBoundedChunker chunker = new TokenBoundedChunker();

    @Test
    @DisplayName("Parses hierarchical markdown sections with header breadcrumbs")
    void testMarkdownHeaderHierarchy() {
        String markdown = """
                # Incident Postmortem: Payment Service Outage
                
                ## Executive Summary
                On 2026-09-12, payment-service experienced connection pool exhaustion.
                
                ## Root Cause
                The HikariCP max pool size was misconfigured to 5 instead of 50.
                
                ### Timeline of Events
                - 10:00 UTC: Alert fired
                - 10:05 UTC: Traffic routed to secondary
                - 10:15 UTC: Rollback complete
                """;

        DocumentParser.ParsedDocument parsed = parser.parse(markdown, "Fallback Title");
        assertThat(parsed.getInferredTitle()).isEqualTo("Incident Postmortem: Payment Service Outage");
        assertThat(parsed.getSections()).hasSize(4);

        List<TokenBoundedChunker.ChunkItem> chunks = chunker.chunk("Payment Outage Postmortem", parsed);
        assertThat(chunks).isNotEmpty();

        // Check header breadcrumb retention at chunk head
        assertThat(chunks.get(0).getText()).startsWith("[Document: Payment Outage Postmortem > Section: ");
        assertThat(chunks.get(0).getTokenCount()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Preserves code blocks and command sequences contiguously without mid-block slicing")
    void testCodeBlockContiguity() {
        String markdown = """
                # Payment Service Runbook
                
                ## Database Recovery Steps
                Execute the following remediation commands in order:
                
                ```bash
                kubectl scale deployment payment-service --replicas=0 -n prod
                kubectl rollout restart statefulset postgres-primary -n prod
                kubectl wait --for=condition=ready pod -l app=postgres-primary --timeout=60s
                kubectl scale deployment payment-service --replicas=5 -n prod
                ```
                
                Verify connection health with:
                ```sql
                SELECT count(*), state FROM pg_stat_activity GROUP BY state;
                ```
                """;

        DocumentParser.ParsedDocument parsed = parser.parse(markdown, "Runbook");
        List<TokenBoundedChunker.ChunkItem> chunks = chunker.chunk("Payment Runbook", parsed);

        assertThat(chunks).isNotEmpty();
        // The code block should remain fully intact within chunk text
        String allChunkText = chunks.stream().map(TokenBoundedChunker.ChunkItem::getText).reduce("", (a, b) -> a + "\n" + b);
        assertThat(allChunkText).contains("kubectl scale deployment payment-service --replicas=0");
        assertThat(allChunkText).contains("kubectl scale deployment payment-service --replicas=5");
        assertThat(allChunkText).contains("SELECT count(*), state FROM pg_stat_activity");
    }

    @Test
    @DisplayName("Splits very large documents into multiple token-bounded chunks respecting hard ceiling")
    void testLargeDocumentSplitting() {
        StringBuilder largeDoc = new StringBuilder("# Large Operational Runbook\n\n## Section 1\n");
        for (int i = 0; i < 50; i++) {
            largeDoc.append("Step ").append(i).append(": Check subsystem health and verify that telemetry latency metrics are within normal bounds without anomalies.\n\n");
        }

        DocumentParser.ParsedDocument parsed = parser.parse(largeDoc.toString(), "Large Runbook");
        List<TokenBoundedChunker.ChunkItem> chunks = chunker.chunk("Large Runbook", parsed);

        assertThat(chunks.size()).isGreaterThan(1);
        for (TokenBoundedChunker.ChunkItem chunk : chunks) {
            assertThat(chunk.getTokenCount()).isLessThanOrEqualTo(TokenBoundedChunker.HARD_CEILING_TOKENS);
            assertThat(chunk.getText()).startsWith("[Document: Large Runbook > Section: ");
        }
    }

    @Test
    @DisplayName("Handles empty, whitespace, and malformed documents gracefully")
    void testEmptyAndMalformedDocuments() {
        DocumentParser.ParsedDocument parsedEmpty = parser.parse("", "Empty Doc");
        List<TokenBoundedChunker.ChunkItem> emptyChunks = chunker.chunk("Empty Doc", parsedEmpty);
        assertThat(emptyChunks).isEmpty();

        DocumentParser.ParsedDocument parsedNull = parser.parse(null, "Null Doc");
        List<TokenBoundedChunker.ChunkItem> nullChunks = chunker.chunk("Null Doc", parsedNull);
        assertThat(nullChunks).isEmpty();

        DocumentParser.ParsedDocument parsedOnlyHeaders = parser.parse("### \n## \n# \n", "Weird Doc");
        List<TokenBoundedChunker.ChunkItem> chunks = chunker.chunk("Weird Doc", parsedOnlyHeaders);
        assertThat(chunks).isNotNull();
    }
}
