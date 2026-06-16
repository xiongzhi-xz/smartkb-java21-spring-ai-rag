package com.smartkb.agent.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.agent.domain.EvalCaseRunResponse;
import com.smartkb.agent.domain.EvalCaseRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class JdbcEvalCaseRunStoreIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("smartkb")
            .withUsername("smartkb")
            .withPassword("smartkb");

    private JdbcTemplate jdbcTemplate;
    private JdbcEvalCaseRunStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new JdbcEvalCaseRunStore(jdbcTemplate, new ObjectMapper());
        store.initializeSchema();
        jdbcTemplate.update("DELETE FROM agent_eval_case_run");
    }

    @Test
    void shouldPersistFindFilterAndSurviveStoreRecreation() {
        store.save(run("run-1", "ticket-project", "E01", EvalCaseRunStatus.PASSED, "2026-06-17T00:00:00Z"));
        store.save(run("run-2", "ticket-project", "E02", EvalCaseRunStatus.PARTIAL, "2026-06-17T00:01:00Z"));
        store.save(run("run-3", "other-project", "E01", EvalCaseRunStatus.FAILED, "2026-06-17T00:02:00Z"));

        EvalCaseRunResponse found = store.findById("run-1").orElseThrow();
        List<EvalCaseRunResponse> allRuns = store.findAll(null, null, null);
        List<EvalCaseRunResponse> projectRuns = store.findAll("ticket-project", null, null);
        List<EvalCaseRunResponse> caseRuns = store.findAll(null, "E01", null);
        List<EvalCaseRunResponse> statusRuns = store.findAll(null, null, EvalCaseRunStatus.PARTIAL);

        assertEquals("run-1", found.id());
        assertEquals(List.of("docs/agent-eval-report.md"), found.evidencePaths());
        assertEquals(List.of("git diff --check"), found.verificationCommands());
        assertEquals(List.of("run-3", "run-2", "run-1"), allRuns.stream().map(EvalCaseRunResponse::id).toList());
        assertEquals(List.of("run-2", "run-1"), projectRuns.stream().map(EvalCaseRunResponse::id).toList());
        assertEquals(List.of("run-3", "run-1"), caseRuns.stream().map(EvalCaseRunResponse::id).toList());
        assertEquals(List.of("run-2"), statusRuns.stream().map(EvalCaseRunResponse::id).toList());

        JdbcEvalCaseRunStore recreatedStore = new JdbcEvalCaseRunStore(jdbcTemplate, new ObjectMapper());
        assertTrue(recreatedStore.findById("run-1").isPresent());
    }

    private EvalCaseRunResponse run(
            String id,
            String projectId,
            String caseId,
            EvalCaseRunStatus status,
            String createdAt
    ) {
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
                createdAt
        );
    }
}
