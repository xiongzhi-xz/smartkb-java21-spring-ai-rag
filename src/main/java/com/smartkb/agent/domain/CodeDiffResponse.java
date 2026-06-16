package com.smartkb.agent.domain;

import java.util.List;

public record CodeDiffResponse(
        boolean success,
        String rootPath,
        boolean gitRepository,
        String query,
        List<DiffFile> files,
        List<ProjectIntakeResponse.SkippedFile> skippedFiles,
        List<String> warnings
) {

    public record DiffFile(
            String path,
            List<DiffLine> lines
    ) {
    }

    public record DiffLine(
            String type,
            Integer oldLineNumber,
            Integer newLineNumber,
            String content
    ) {
    }
}
