package com.smartkb.agent.domain;

public record CodeTreeRequest(
        String rootPath,
        Integer maxFiles,
        Integer maxDepth
) {
}
