# Eval Run JDBC Integration Test Plan

## Goal

Add automated confidence for `JdbcEvalCaseRunStore` without making the default `mvn test` depend on Docker or a local PostgreSQL instance.

## Current Coverage

Default automated tests already cover:

- `EvalCaseRunService` validation and normalization.
- `InMemoryEvalCaseRunStore` save, find, newest-first ordering, and filters.
- TicketRush import idempotency through the active service.
- Eval report aggregation.
- Controller request and response shape.

Manual JDBC smoke coverage already verified:

- JDBC mode startup.
- Table creation.
- API create/list/import/report.
- Restart persistence.
- PostgreSQL row count.

See `docs/EVAL_RUN_JDBC_VERIFICATION.md`.

## Recommended Approach

Introduce a separate integration-test profile instead of putting JDBC container tests in the default unit-test lifecycle.

Recommended Maven shape:

- Keep Surefire for fast unit and MVC tests.
- Add Failsafe for `*IT.java` tests.
- Add a Maven profile such as `integration-tests`.
- Add Testcontainers dependencies only under that profile if the project wants the dependency isolated.

Example command:

```powershell
mvn -Pintegration-tests verify
```

Default command remains:

```powershell
mvn test
```

## Proposed Test

Add `JdbcEvalCaseRunStoreIT` with PostgreSQL Testcontainers.

Test cases:

- Starts a PostgreSQL container.
- Creates `JdbcTemplate` and `ObjectMapper`.
- Instantiates `JdbcEvalCaseRunStore`.
- Calls `initializeSchema()`.
- Saves three runs with different `projectId`, `caseId`, and `status`.
- Verifies `findById`.
- Verifies newest-first ordering for `findAll(null, null, null)`.
- Verifies project, case, and status filters.
- Creates a second store instance against the same database and verifies a previously saved run still exists.

## Why Not Add It To Default Tests

- Testcontainers requires Docker.
- CI or interview machines may not have Docker running.
- The current default test suite is fast and deterministic.
- JDBC persistence is optional; memory remains the default store.

## Dependencies To Add Later

Candidate dependencies:

- `org.testcontainers:junit-jupiter`
- `org.testcontainers:postgresql`

Candidate plugin:

- `maven-failsafe-plugin`

Do not add these dependencies until the project is ready to support an explicit integration-test profile.

## Acceptance Criteria

- `mvn test` still passes without Docker.
- `mvn -Pintegration-tests verify` runs `JdbcEvalCaseRunStoreIT`.
- The integration test creates an isolated PostgreSQL database and does not depend on local Docker Compose volumes.
- The integration test does not print secrets or environment values.
