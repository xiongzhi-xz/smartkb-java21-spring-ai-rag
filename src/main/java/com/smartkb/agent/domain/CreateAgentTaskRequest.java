package com.smartkb.agent.domain;

public record CreateAgentTaskRequest(
        String projectId,
        String title,
        String goal,
        String riskLevel
) {
}
