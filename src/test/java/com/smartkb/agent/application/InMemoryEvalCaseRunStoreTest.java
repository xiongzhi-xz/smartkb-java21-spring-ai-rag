package com.smartkb.agent.application;

import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryEvalCaseRunStoreTest {

    private final InMemoryEvalCaseRunStore store = new InMemoryEvalCaseRunStore();

    @Test
    void shouldSaveAndFindRunById() {
        EvalCaseRunResponse run = run("run-1", "ticket-project", "E01", EvalCaseRunStatus.PASSED);

        store.save(run);

        assertEquals(run, store.findById("run-1").orElseThrow());
    }

    @Test
    void shouldListNewestFirstAndApplyFilters() {
        store.save(run("run-1", "ticket-project", "E01", EvalCaseRunStatus.PASSED));
        store.save(run("run-2", "ticket-project", "E02", EvalCaseRunStatus.PARTIAL));
        store.save(run("run-3", "other-project", "E01", EvalCaseRunStatus.FAILED));

        List<EvalCaseRunResponse> allRuns = store.findAll(null, null, null);
        List<EvalCaseRunResponse> projectRuns = store.findAll("ticket-project", null, null);
        List<EvalCaseRunResponse> caseRuns = store.findAll(null, "E01", null);
        List<EvalCaseRunResponse> statusRuns = store.findAll(null, null, EvalCaseRunStatus.PARTIAL);

        assertEquals(List.of("run-3", "run-2", "run-1"), allRuns.stream().map(EvalCaseRunResponse::id).toList());
        assertEquals(List.of("run-2", "run-1"), projectRuns.stream().map(EvalCaseRunResponse::id).toList());
        assertEquals(List.of("run-3", "run-1"), caseRuns.stream().map(EvalCaseRunResponse::id).toList());
        assertEquals(List.of("run-2"), statusRuns.stream().map(EvalCaseRunResponse::id).toList());
    }

    @Test
    void shouldReturnEmptyWhenRunIsMissing() {
        assertTrue(store.findById("missing").isEmpty());
    }

    private EvalCaseRunResponse run(String id, String projectId, String caseId, EvalCaseRunStatus status) {
        return new EvalCaseRunResponse(
                id,
                projectId,
                caseId,
                "Eval " + caseId,
                status,
                8,
                10,
                0,
                3,
                60L,
                List.of("docs/agent-eval-report.md"),
                List.of("git diff --check"),
                "Recorded " + caseId,
                status == EvalCaseRunStatus.FAILED ? "Missing evidence" : null,
                "2026-06-17T00:00:00Z"
        );
    }
}
