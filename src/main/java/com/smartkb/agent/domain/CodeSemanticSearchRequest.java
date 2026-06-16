package com.smartkb.agent.domain;

public record CodeSemanticSearchRequest(
        String rootPath,
        String query,
        Integer maxResults,
        Integer maxFileBytes,
        Integer maxChunkChars
) {
}
