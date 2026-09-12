package com.resolveiq.backend.integrations.git;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * High-fidelity Git client provider for automated tests and offline simulation.
 * Returns authentic code diffs for canonical scenarios (e.g. payment-service connection pool regression).
 */
@Component
public class MockGitClient implements GitClient {

    @Override
    public CommitDiffDto getCommitDiff(String repoName, String baseCommit, String headCommit, String accessToken) {
        if (repoName != null && repoName.contains("payment-service")) {
            return new CommitDiffDto(
                    repoName,
                    baseCommit != null ? baseCommit : "v2.7",
                    headCommit != null ? headCommit : "d7a4b81",
                    "ci-deployer",
                    "feat(pool): restrict connection pool for cost optimization",
                    List.of("src/main/resources/application.yml", "src/main/java/com/resolveiq/payment/config/DatabaseConfig.java"),
                    5,
                    2,
                    """
                    --- a/src/main/resources/application.yml
                    +++ b/src/main/resources/application.yml
                    @@ -12,4 +12,4 @@ spring:
                       datasource:
                         hikari:
                    -      maximum-pool-size: 50
                    +      maximum-pool-size: 5
                           connection-timeout: 30000
                    """
            );
        }

        return new CommitDiffDto(
                repoName,
                baseCommit != null ? baseCommit : "HEAD~1",
                headCommit != null ? headCommit : "HEAD",
                "developer",
                "chore: update dependencies and configuration",
                List.of("pom.xml"),
                1,
                1,
                """
                --- a/pom.xml
                +++ b/pom.xml
                @@ -10,3 +10,3 @@
                -<version>1.0.0</version>
                +<version>1.0.1</version>
                """
        );
    }

    @Override
    public List<String> listRecentCommits(String repoName, int limit, String accessToken) {
        return List.of(
                "d7a4b81: feat(pool): restrict connection pool for cost optimization",
                "c902be4: fix(auth): token expiration handling",
                "b148ac2: chore(deps): upgrade spring-boot-starter"
        );
    }
}
