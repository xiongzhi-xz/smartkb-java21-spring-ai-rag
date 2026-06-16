package com.smartkb.agent.domain;

public record AgentTaskEvent(
        AgentTaskStatus status,
        String note,
        String createdAt
) {
}
