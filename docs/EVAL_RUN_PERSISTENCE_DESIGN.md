# Eval Run Persistence Design

## Current State

Eval runs use a store abstraction behind `EvalCaseRunService`.

- Default mode: `InMemoryEvalCaseRunStore`
- Optional mode: `JdbcEvalCaseRunStore`

Enable JDBC persistence with:

```yaml
smartkb:
  agent:
    eval-run:
      persistence: jdbc
```

The default remains memory so local tests and demos without PostgreSQL keep working.

The project already has:

- PostgreSQL runtime dependency
- `spring-boot-starter-jdbc`
- existing `JdbcTemplate` usage
- `docker/init-db.sql` for database bootstrap

The project does not currently use Flyway, Liquibase, JPA, or MyBatis.

## Implemented Approach

Use a small JDBC store with idempotent table creation on startup. Do not introduce a migration framework for this narrow Agent-platform feature yet.

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
```

## Store Boundary

The store abstraction is `EvalCaseRunStore` with:

- `save(EvalCaseRunResponse run)`
- `findById(String id)`
- `findAll(String projectId, String caseId, EvalCaseRunStatus status)`

Validation stays in `EvalCaseRunService`. Stores only persist or retrieve normalized domain responses.

## JSON Fields

Store `evidencePaths` and `verificationCommands` as JSON text in v1 to avoid introducing a JSONB mapping abstraction. PostgreSQL can still query these fields later if they are migrated to JSONB.

## Startup Behavior

When JDBC persistence is enabled, `JdbcEvalCaseRunStore` creates the table and indexes with `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS`. If the database is unavailable in JDBC mode, startup should fail because persisted eval runs are no longer a soft optional cache.

## Test Plan

- Existing service tests cover validation and aggregation.
- `InMemoryEvalCaseRunStoreTest` covers default store ordering and filters.
- Controller tests remain unchanged because the controller does not know whether storage is memory or JDBC.
- `JdbcEvalCaseRunStoreIT` runs under the `integration-tests` Maven profile with Testcontainers when Docker is available.
- Manual Docker Compose JDBC smoke verification is recorded in `docs/EVAL_RUN_JDBC_VERIFICATION.md`.
- Run `mvn test`, `mvn -Pintegration-tests verify`, and `git diff --check`.

## Not Doing Yet

- No Flyway/Liquibase adoption in this step.
- No schema changes to `vector_store`.
- No cross-project eval dashboard persistence.
- No deleting or updating historical runs from the UI until audit requirements are clearer.
