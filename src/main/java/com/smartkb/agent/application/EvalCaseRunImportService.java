package com.smartkb.agent.application;

import com.smartkb.agent.domain.CreateEvalCaseRunRequest;
import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import com.smartkb.agent.domain.ImportEvalCaseRunsResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EvalCaseRunImportService {

    private static final String PROJECT_ID = "ticket-project";
    private static final String REPORT_PATH = "docs/agent-eval-report.md";

    private final EvalCaseRunService evalCaseRunService;

    public EvalCaseRunImportService(EvalCaseRunService evalCaseRunService) {
        this.evalCaseRunService = evalCaseRunService;
    }

    public ImportEvalCaseRunsResponse importTicketRushReport() {
        List<EvalCaseRunResponse> imported = new ArrayList<>();
        int skipped = 0;

        for (SeedRun seed : seedRuns()) {
            if (!evalCaseRunService.list(PROJECT_ID, seed.caseId(), null).isEmpty()) {
                skipped++;
                continue;
            }
            imported.add(evalCaseRunService.create(toRequest(seed)));
        }

        return new ImportEvalCaseRunsResponse(imported.size(), skipped, imported);
    }

    private CreateEvalCaseRunRequest toRequest(SeedRun seed) {
        return new CreateEvalCaseRunRequest(
                PROJECT_ID,
                seed.caseId(),
                seed.title(),
                EvalCaseRunStatus.PASSED,
                2,
                2,
                0,
                seed.toolCallCount(),
                null,
                List.of(REPORT_PATH),
                List.of("git status --short", "git diff --check"),
                seed.summary(),
                null
        );
    }

    private List<SeedRun> seedRuns() {
        return List.of(
                new SeedRun("E01", "TicketRush project intake", 5, "Identified current stage, completed work, unfinished k6 benchmarks, risks, and the single next step."),
                new SeedRun("E02", "Explain RocketMQ async order flow", 19, "Mapped publisher, binding, consumer, idempotent order creation, retry configuration, and compensation evidence."),
                new SeedRun("E03", "Explain Redis Lua oversell protection", 10, "Located Lua script, inventory hash fields, idempotent key, return-code mapping, and strategy differences."),
                new SeedRun("E04", "Judge Docker Compose prerequisites", 4, "Identified local JAR mount, core dependencies, health endpoints, and compose verification path."),
                new SeedRun("E05", "Generate minimal k6 benchmark steps", 5, "Produced executable k6 steps covering build, startup, inventory preload, scenario commands, metrics, and result template."),
                new SeedRun("E06", "Find current unfinished tasks", 4, "Prioritized unfinished work from SPEC/HANDOFF and kept the next step focused on first local k6 benchmark data."),
                new SeedRun("E07", "Review risk of a small config change", 3, "Reviewed Docker profile health-check risk, impact scope, and validation commands without changing code."),
                new SeedRun("E08", "Add document verification record", 3, "Detected equivalent verification record already existed and avoided producing a meaningless TicketRush diff."),
                new SeedRun("E09", "Judge whether runtime data should be committed", 4, "Identified RocketMQ store as ignored runtime data and recommended not committing or deleting it."),
                new SeedRun("E10", "Generate interview explanation", 3, "Connected TicketRush high-concurrency flow, Redis Lua, RocketMQ, Sentinel, Docker Compose, and SmartKB eval value.")
        );
    }

    private record SeedRun(
            String caseId,
            String title,
            int toolCallCount,
            String summary
    ) {
    }
}
