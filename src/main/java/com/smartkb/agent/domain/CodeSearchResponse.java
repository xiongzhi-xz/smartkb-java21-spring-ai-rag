package com.smartkb.agent.domain;

import java.util.List;

public record CodeSearchResponse(
        boolean success,
        String rootPath,
        String query,
        List<CodeMatch> matches,
        List<ProjectIntakeResponse.SkippedFile> skippedFiles,
        List<String> warnings
) {

    public record CodeMatch(
            String path,
            int lineNumber,
            String line
    ) {
    }
}
