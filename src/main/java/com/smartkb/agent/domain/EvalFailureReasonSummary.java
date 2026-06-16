package com.smartkb.agent.domain;

public record EvalFailureReasonSummary(
        String reason,
        long count
) {
}
