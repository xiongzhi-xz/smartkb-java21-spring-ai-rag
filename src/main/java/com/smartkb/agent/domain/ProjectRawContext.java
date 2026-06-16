package com.smartkb.agent.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Raw files and command output collected before summarization.
 */
public record ProjectRawContext(
        Path rootPath,
        Map<String, String> fileContents,
        List<String> fileTree,
        List<ProjectIntakeResponse.SkippedFile> skippedFiles,
        List<String> warnings,
        GitInfo gitInfo
) {

    public record GitInfo(
            boolean gitRepository,
            String statusShort,
            List<String> latestCommits,
            List<String> warnings
    ) {
    }
}
