package com.smartkb.agent.domain;

public record CodeChunkRequest(
        String rootPath,
        Integer maxChunks,
        Integer maxFileBytes,
        Integer maxChunkChars
) {
}
