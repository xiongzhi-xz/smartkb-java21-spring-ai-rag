# Eval Run Persistence Design

## Current State

Eval runs are stored in memory through `EvalCaseRunService`. This is enough for API shape, frontend workflow, aggregation, tests, and importing the existing TicketRush E01-E10 report. The limitation is that runs disappear after application restart.

The project already has:

- PostgreSQL runtime dependency
- `spring-boot-starter-jdbc`
- existing `JdbcTemplate` usage
- `docker/init-db.sql` for database bootstrap

The project does not currently use Flyway, Liquibase, JPA, or MyBatis.

## Recommendation

Use a small JDBC repository with idempotent table creation on startup. Do not introduce a migration framework for this narrow Agent-platform feature yet.

Reasons:

- It matches existing `JdbcTemplate` style.
- It avoids adding JPA or migration dependencies.
- The table is additive and isolated from the RAG `vector_store` table.
- It keeps the first persistence step easy to test and roll back.

## Proposed Table

```sql
CREATE TABLE IF NOT EXISTS agent_eval_case_run (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(128),
    case_id VARCHAR(64) NOT NULL,
    title TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    score INTEGER,
    max_score INTEGER,
    human_interventions INTEGER,
    tool_call_count INTEGER,
    duration_seconds BIGINT,
    evidence_paths TEXT NOT NULL DEFAULT '[]',
    verification_commands TEXT NOT NULL DEFAULT '[]',
    summary TEXT,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_case_run_project
    ON agent_eval_case_run(project_id);

CREATE INDEX IF NOT EXISTS idx_agent_eval_case_run_case
    ON agent_eval_case_run(case_id);

CREATE INDEX IF NOT EXISTS idx_agent_eval_case_run_status
    ON agent_eval_case_run(status);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_eval_case_run_project_case_title
    ON agent_eval_case_run(COALESCE(project_id, ''), case_id, title);
```

## Repository Boundary

Add `EvalCaseRunRepository` with:

- `save(EvalCaseRunResponse run)`
- `findById(String id)`
- `findAll(String projectId, String caseId, EvalCaseRunStatus status)`
- `existsByProjectIdAndCaseId(String projectId, String caseId)`

Keep validation in `EvalCaseRunService`. The repository should only persist normalized domain responses.

## JSON Fields

Store `evidencePaths` and `verificationCommands` as JSON text in v1 to avoid introducing a JSONB mapping abstraction. PostgreSQL can still query these fields later if they are migrated to JSONB.

## Startup Behavior

Create the table in a small bootstrap method guarded by `CREATE TABLE IF NOT EXISTS`. If the database is unavailable, fail fast in normal application startup because persisted eval runs are not a soft optional cache once enabled.

## Test Plan

- Keep existing service tests for validation and aggregation.
- Add repository tests with an embedded or test PostgreSQL profile when available.
- Add controller tests unchanged because the controller should not know whether storage is memory or JDBC.
- Run `mvn test` and `git diff --check`.

## Not Doing Yet

- No Flyway/Liquibase adoption in this step.
- No schema changes to `vector_store`.
- No cross-project eval dashboard persistence.
- No deleting or updating historical runs from the UI until audit requirements are clearer.
