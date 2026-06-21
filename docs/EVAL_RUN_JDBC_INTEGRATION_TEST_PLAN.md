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

## Implemented Approach

Introduce a separate integration-test profile instead of putting JDBC container tests in the default unit-test lifecycle.

Implemented Maven shape:

- Keep Surefire for fast unit and MVC tests.
- Add Failsafe for `*IT.java` tests under the `integration-tests` profile.
- Add Testcontainers as `test` scope dependencies so `src/test/java` can compile in the default lifecycle.
- Guard Testcontainers execution with `@Testcontainers(disabledWithoutDocker = true)`.

Example command:

```powershell
mvn -Pintegration-tests verify
```

Default command remains:

```powershell
mvn test
```

## Implemented Test

`JdbcEvalCaseRunStoreIT` uses PostgreSQL Testcontainers.

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
- CI or review machines may not have Docker running.
- The current default test suite is fast and deterministic.
- JDBC persistence is optional; memory remains the default store.

## Dependencies Added

- `org.testcontainers:junit-jupiter`
- `org.testcontainers:postgresql`

Plugin added under the `integration-tests` profile:

- `maven-failsafe-plugin`

## Acceptance Criteria

- `mvn test` still passes without Docker.
- `mvn -Pintegration-tests verify` runs `JdbcEvalCaseRunStoreIT`.
- The integration test creates an isolated PostgreSQL database and does not depend on local Docker Compose volumes.
- The integration test does not print secrets or environment values.

## Verification Record

Date: 2026-06-17

- `mvn test`: passed, 74 tests.
- `mvn -Pintegration-tests verify`: passed.
- `JdbcEvalCaseRunStoreIT`: skipped on this Windows Docker Desktop environment because Testcontainers could not find a valid Java Docker client configuration through npipe.
- Manual Docker Compose JDBC smoke test already verified live PostgreSQL behavior; see `docs/EVAL_RUN_JDBC_VERIFICATION.md`.

On a standard Docker environment supported by Testcontainers, `JdbcEvalCaseRunStoreIT` should run through Failsafe and validate the JDBC store against an isolated PostgreSQL container.
