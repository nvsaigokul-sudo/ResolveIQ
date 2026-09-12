package com.resolveiq.backend.integrations.git;

import java.util.List;

public interface GitClient {

    CommitDiffDto getCommitDiff(String repoName, String baseCommit, String headCommit, String accessToken);

    List<String> listRecentCommits(String repoName, int limit, String accessToken);
}
