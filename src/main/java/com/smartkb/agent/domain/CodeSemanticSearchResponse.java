package com.smartkb.agent.domain;

import java.util.List;

public record CodeSemanticSearchResponse(
        boolean success,
        String rootPath,
        String query,
        List<SemanticMatch> matches,
        List<ProjectIntakeResponse.SkippedFile> skippedFiles,
        List<String> warnings
) {

    public record SemanticMatch(
            String path,
            int startLine,
            int endLine,
            int score,
            List<String> matchedTerms,
            String content
    ) {
    }
}
