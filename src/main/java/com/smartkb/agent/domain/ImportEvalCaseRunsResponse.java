package com.smartkb.agent.domain;

import java.util.List;

public record ImportEvalCaseRunsResponse(
        int importedCount,
        int skippedCount,
        List<EvalCaseRunResponse> runs
) {
}
