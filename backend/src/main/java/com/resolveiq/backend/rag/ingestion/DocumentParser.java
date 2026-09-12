package com.resolveiq.backend.rag.ingestion;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic parser for markdown documents, postmortems, and runbooks per PRD §22, §25.
 */
@Component
public class DocumentParser {

    private static final Pattern HEADER_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[a-zA-Z0-9_-]*\\n[\\s\\S]*?```");

    public static class Section {
        private final String headerPath;
        private final String title;
        private final int level;
        private final String content;
        private final boolean isCodeBlock;

        public Section(String headerPath, String title, int level, String content, boolean isCodeBlock) {
            this.headerPath = headerPath;
            this.title = title;
            this.level = level;
            this.content = content;
            this.isCodeBlock = isCodeBlock;
        }

        public String getHeaderPath() {
            return headerPath;
        }

        public String getTitle() {
            return title;
        }

        public int getLevel() {
            return level;
        }

        public String getContent() {
            return content;
        }

        public boolean isCodeBlock() {
            return isCodeBlock;
        }
    }

    public static class ParsedDocument {
        private final String inferredTitle;
        private final List<Section> sections;

        public ParsedDocument(String inferredTitle, List<Section> sections) {
            this.inferredTitle = inferredTitle;
            this.sections = sections;
        }

        public String getInferredTitle() {
            return inferredTitle;
        }

        public List<Section> getSections() {
            return sections;
        }
    }

    /**
     * Parses raw markdown or text into structured sections with header hierarchies.
     */
    public ParsedDocument parse(String text, String fallbackTitle) {
        if (text == null || text.isBlank()) {
            return new ParsedDocument(fallbackTitle != null ? fallbackTitle : "Untitled Document", List.of());
        }

        String[] lines = text.split("\\r?\\n");
        List<Section> sections = new ArrayList<>();
        Deque<String> headerStack = new ArrayDeque<>();
        Deque<Integer> levelStack = new ArrayDeque<>();

        String inferredTitle = fallbackTitle;
        StringBuilder currentSectionContent = new StringBuilder();
        String currentSectionTitle = "Overview";
        int currentLevel = 1;

        boolean inCodeBlock = false;
        StringBuilder codeBlockContent = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Close code block
                    codeBlockContent.append(line).append("\n");
                    currentSectionContent.append(codeBlockContent);
                    inCodeBlock = false;
                    codeBlockContent.setLength(0);
                    continue;
                } else {
                    inCodeBlock = true;
                    codeBlockContent.append(line).append("\n");
                    continue;
                }
            }

            if (inCodeBlock) {
                codeBlockContent.append(line).append("\n");
                continue;
            }

            Matcher headerMatcher = HEADER_PATTERN.matcher(line);
            if (headerMatcher.matches()) {
                // Flush previous section
                if (currentSectionContent.length() > 0) {
                    sections.add(new Section(buildHeaderPath(headerStack), currentSectionTitle, currentLevel,
                            currentSectionContent.toString().trim(), false));
                    currentSectionContent.setLength(0);
                }

                int level = headerMatcher.group(1).length();
                String title = headerMatcher.group(2).trim();

                if (level == 1) {
                    inferredTitle = title;
                }

                // Adjust header hierarchy stack
                while (!levelStack.isEmpty() && levelStack.peek() >= level) {
                    levelStack.pop();
                    headerStack.pop();
                }
                levelStack.push(level);
                headerStack.push(title);

                currentSectionTitle = title;
                currentLevel = level;
            } else {
                currentSectionContent.append(line).append("\n");
            }
        }

        // Flush any remaining section
        if (currentSectionContent.length() > 0) {
            sections.add(new Section(buildHeaderPath(headerStack), currentSectionTitle, currentLevel,
                    currentSectionContent.toString().trim(), false));
        }

        return new ParsedDocument(inferredTitle != null ? inferredTitle : "Untitled Document", sections);
    }

    private String buildHeaderPath(Deque<String> headerStack) {
        if (headerStack.isEmpty()) {
            return "General";
        }
        List<String> list = new ArrayList<>(headerStack);
        Collections.reverse(list);
        return String.join(" > ", list);
    }
}
