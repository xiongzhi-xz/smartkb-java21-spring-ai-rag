package com.smartkb.agent.domain;

import java.util.List;

public record CodeTreeResponse(
        boolean success,
        String rootPath,
        List<CodeFile> files,
        List<ProjectIntakeResponse.SkippedFile> skippedFiles,
        List<String> warnings
) {

    public record CodeFile(
            String path,
            long bytes,
            String extension
    ) {
    }
}
