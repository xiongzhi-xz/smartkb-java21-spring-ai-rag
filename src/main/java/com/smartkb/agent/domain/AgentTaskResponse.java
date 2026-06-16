package com.smartkb.agent.domain;

import java.util.List;

public record AgentTaskResponse(
        String id,
        String projectId,
        String title,
        String goal,
        AgentTaskStatus status,
        String riskLevel,
        String plan,
        String verification,
        String resultSummary,
        String createdAt,
        String updatedAt,
        List<AgentTaskEvent> events
) {
}
