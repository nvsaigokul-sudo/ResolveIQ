package com.resolveiq.backend.integrations.git;

import java.util.List;

public record CommitDiffDto(
        String repoName,
        String baseCommit,
        String headCommit,
        String author,
        String message,
        List<String> filesChanged,
        int insertions,
        int deletions,
        String diffPatch
) {}
