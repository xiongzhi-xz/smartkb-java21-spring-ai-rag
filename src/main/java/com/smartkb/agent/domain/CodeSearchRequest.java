package com.smartkb.agent.domain;

public record CodeSearchRequest(
        String rootPath,
        String query,
        Integer maxResults,
        Integer maxFileBytes
) {
}
