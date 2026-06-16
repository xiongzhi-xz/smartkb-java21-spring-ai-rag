package com.smartkb.agent.domain;

public record ImportHighAuthorityMemoryRequest(
        String projectId,
        String rootPath,
        Integer maxFileBytes
) {
}
