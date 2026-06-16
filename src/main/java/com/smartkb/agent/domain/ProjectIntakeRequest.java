package com.smartkb.agent.domain;

/**
 * Project Intake request.
 */
public record ProjectIntakeRequest(
        String rootPath,
        String goal,
        Boolean includeCodeTree,
        Integer maxFiles,
        Integer maxFileBytes
) {
}
