package com.smartkb.agent.domain;

public record TransitionAgentTaskRequest(
        AgentTaskStatus targetStatus,
        String note,
        String plan,
        String verification,
        String resultSummary,
        Boolean verificationPassed
) {
}
