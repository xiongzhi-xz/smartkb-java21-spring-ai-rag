package com.smartkb.agent.domain;

import java.util.List;

public record CodeChunkResponse(
        boolean success,
        String rootPath,
        List<CodeChunk> chunks,
        List<ProjectIntakeResponse.SkippedFile> skippedFiles,
        List<String> warnings
) {

    public record CodeChunk(
            String path,
            int startLine,
            int endLine,
            String content
    ) {
    }
}
