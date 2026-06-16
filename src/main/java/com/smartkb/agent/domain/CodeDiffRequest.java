package com.smartkb.agent.domain;

public record CodeDiffRequest(
        String rootPath,
        String query,
        Integer maxLines
) {
}
