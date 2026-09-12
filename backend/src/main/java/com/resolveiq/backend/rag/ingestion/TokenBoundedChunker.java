package com.resolveiq.backend.rag.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Token-bounded chunker preserving section headers, paragraphs, and code blocks per PRD §22, §25.
 * Estimates tokens using the standard 4-characters-per-token heuristic.
 */
@Component
public class TokenBoundedChunker {

    public static final int TARGET_MIN_TOKENS = 150;
    public static final int TARGET_MAX_TOKENS = 500;
    public static final int HARD_CEILING_TOKENS = 800;
    public static final int OVERLAP_TOKENS = 50;

    public static class ChunkItem {
        private final int index;
        private final String text;
        private final int tokenCount;
        private final String headerPath;

        public ChunkItem(int index, String text, int tokenCount, String headerPath) {
            this.index = index;
            this.text = text;
            this.tokenCount = tokenCount;
            this.headerPath = headerPath;
        }

        public int getIndex() {
            return index;
        }

        public String getText() {
            return text;
        }

        public int getTokenCount() {
            return tokenCount;
        }

        public String getHeaderPath() {
            return headerPath;
        }
    }

    /**
     * Chunks a parsed document into token-bounded pieces with header breadcrumbs.
     */
    public List<ChunkItem> chunk(String docTitle, DocumentParser.ParsedDocument parsedDoc) {
        if (parsedDoc == null || parsedDoc.getSections().isEmpty()) {
            return List.of();
        }

        List<ChunkItem> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (DocumentParser.Section section : parsedDoc.getSections()) {
            String sectionContent = section.getContent();
            if (sectionContent == null || sectionContent.isBlank()) {
                continue;
            }

            String breadcrumb = String.format("[Document: %s > Section: %s]\n\n", docTitle, section.getHeaderPath());
            int breadcrumbTokens = estimateTokens(breadcrumb);

            int sectionTokens = estimateTokens(sectionContent);

            // If the whole section fits within the target max token budget, keep it as a single chunk
            if (breadcrumbTokens + sectionTokens <= TARGET_MAX_TOKENS) {
                String fullText = breadcrumb + sectionContent;
                chunks.add(new ChunkItem(chunkIndex++, fullText, estimateTokens(fullText), section.getHeaderPath()));
                continue;
            }

            // Otherwise, split section by paragraphs or code blocks
            List<String> paragraphs = splitIntoParagraphs(sectionContent);
            StringBuilder currentChunkText = new StringBuilder(breadcrumb);
            int currentTokens = breadcrumbTokens;
            String lastParagraph = "";

            for (String para : paragraphs) {
                int paraTokens = estimateTokens(para);

                // If a single paragraph exceeds hard ceiling (e.g. huge stack trace), split by sentences/lines
                if (paraTokens > (HARD_CEILING_TOKENS - breadcrumbTokens)) {
                    // First flush existing buffer if not empty
                    if (currentTokens > breadcrumbTokens) {
                        chunks.add(new ChunkItem(chunkIndex++, currentChunkText.toString().trim(),
                                estimateTokens(currentChunkText.toString()), section.getHeaderPath()));
                        currentChunkText = new StringBuilder(breadcrumb);
                        currentTokens = breadcrumbTokens;
                    }

                    List<String> subSlices = splitLargeParagraph(para, HARD_CEILING_TOKENS - breadcrumbTokens);
                    for (String slice : subSlices) {
                        String fullSlice = breadcrumb + slice;
                        chunks.add(new ChunkItem(chunkIndex++, fullSlice, estimateTokens(fullSlice), section.getHeaderPath()));
                    }
                    continue;
                }

                if (currentTokens + paraTokens > TARGET_MAX_TOKENS) {
                    // Emit chunk
                    chunks.add(new ChunkItem(chunkIndex++, currentChunkText.toString().trim(),
                            estimateTokens(currentChunkText.toString()), section.getHeaderPath()));

                    // Start new chunk with breadcrumb + overlap from last paragraph
                    currentChunkText = new StringBuilder(breadcrumb);
                    if (!lastParagraph.isBlank() && estimateTokens(lastParagraph) <= OVERLAP_TOKENS * 2) {
                        currentChunkText.append("... ").append(lastParagraph).append("\n\n");
                    }
                    currentChunkText.append(para).append("\n\n");
                    currentTokens = estimateTokens(currentChunkText.toString());
                } else {
                    currentChunkText.append(para).append("\n\n");
                    currentTokens += paraTokens;
                }
                lastParagraph = para;
            }

            // Flush remaining chunk
            if (currentTokens > breadcrumbTokens) {
                chunks.add(new ChunkItem(chunkIndex++, currentChunkText.toString().trim(),
                        estimateTokens(currentChunkText.toString()), section.getHeaderPath()));
            }
        }

        return chunks;
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Heuristic: ~4 characters per token
        return (int) Math.ceil(text.length() / 4.0);
    }

    private List<String> splitIntoParagraphs(String content) {
        String[] raw = content.split("\\n\\s*\\n");
        List<String> result = new ArrayList<>();
        boolean inCodeBlock = false;
        StringBuilder currentCodeBlock = new StringBuilder();

        for (String p : raw) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("```") && !inCodeBlock) {
                if (trimmed.endsWith("```") && trimmed.length() > 3) {
                    // self-contained code block
                    result.add(trimmed);
                } else {
                    inCodeBlock = true;
                    currentCodeBlock.append(trimmed).append("\n\n");
                }
            } else if (inCodeBlock) {
                currentCodeBlock.append(trimmed).append("\n\n");
                if (trimmed.endsWith("```")) {
                    inCodeBlock = false;
                    result.add(currentCodeBlock.toString().trim());
                    currentCodeBlock.setLength(0);
                }
            } else {
                result.add(trimmed);
            }
        }

        if (currentCodeBlock.length() > 0) {
            result.add(currentCodeBlock.toString().trim());
        }

        return result;
    }

    private List<String> splitLargeParagraph(String para, int maxTokens) {
        int maxChars = maxTokens * 4;
        List<String> slices = new ArrayList<>();
        String[] lines = para.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            if (sb.length() + line.length() + 1 > maxChars && sb.length() > 0) {
                slices.add(sb.toString().trim());
                sb.setLength(0);
            }
            sb.append(line).append("\n");
        }

        if (sb.length() > 0) {
            slices.add(sb.toString().trim());
        }

        return slices;
    }
}
