# Eval Run JDBC Verification

This guide verifies the optional JDBC-backed Eval Run store without changing the default local demo mode.

## Scope

- Verify that `smartkb.agent.eval-run.persistence=jdbc` starts with PostgreSQL.
- Verify that Eval Run create, list, get, import, and report APIs work after switching from memory to JDBC.
- Verify that the `agent_eval_case_run` table is created idempotently.

This guide does not require Flyway, Liquibase, JPA, or MyBatis.

## Preconditions

- PostgreSQL is available with the same connection settings used by SmartKB.
- The application can reach PostgreSQL through `spring.datasource.url`.
- Do not commit generated database data or Docker volume contents.

For Docker Compose, the app container can reach PostgreSQL through:

```text
jdbc:postgresql://postgres:5432/smartkb
```

For running Spring Boot directly from the host, expose PostgreSQL to the host first or use an existing local PostgreSQL instance:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/smartkb
```

## Start With JDBC Store

Run the app with the property enabled:

```powershell
$env:SMARTKB_AGENT_EVAL_RUN_PERSISTENCE="jdbc"
mvn spring-boot:run
```

Or pass it as a JVM argument:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--smartkb.agent.eval-run.persistence=jdbc"
```

Expected startup behavior:

- Spring creates `JdbcEvalCaseRunStore`.
- Spring does not create `InMemoryEvalCaseRunStore`.
- PostgreSQL receives `CREATE TABLE IF NOT EXISTS agent_eval_case_run`.
- Startup fails fast if PostgreSQL is unavailable.

## Smoke Test Commands

Create one eval run:

```powershell
curl.exe -s -X POST "http://localhost:8080/api/agent/eval/runs" `
  -H "Content-Type: application/json" `
  -d '{ "projectId": "ticket-project", "caseId": "E-JDBC", "title": "JDBC persistence smoke test", "status": "PASSED", "score": 1, "maxScore": 1, "humanInterventions": 0, "toolCallCount": 1, "durationSeconds": 10, "evidencePaths": ["docs/EVAL_RUN_JDBC_VERIFICATION.md"], "verificationCommands": ["curl.exe POST /api/agent/eval/runs"], "summary": "JDBC eval run persisted.", "failureReason": null }'
```

List the run:

```powershell
curl.exe -s "http://localhost:8080/api/agent/eval/runs?projectId=ticket-project&caseId=E-JDBC"
```

Import the TicketRush E01-E10 records:

```powershell
curl.exe -s -X POST "http://localhost:8080/api/agent/eval/runs/import-ticket-rush-report"
```

Generate the aggregate report:

```powershell
curl.exe -s "http://localhost:8080/api/agent/eval/report?projectId=ticket-project"
```

Expected API behavior:

- Created runs survive an application restart.
- List results are sorted by `created_at DESC`.
- Import remains idempotent per `projectId + caseId`.
- Report totals include persisted runs.

## Database Checks

From a PostgreSQL shell:

```sql
\dt agent_eval_case_run
SELECT id, project_id, case_id, status, created_at
FROM agent_eval_case_run
ORDER BY created_at DESC
LIMIT 5;
```

Expected database behavior:

- The table exists after startup in JDBC mode.
- `evidence_paths` and `verification_commands` are stored as JSON text.
- Re-running startup does not fail because table and index creation is idempotent.

## Automated Coverage

Covered by automated tests:

- Service validation and run creation.
- In-memory store save, read, ordering, and filters.
- Import idempotency.
- Report aggregation.
- Controller API shape.

Not covered yet:

- Live PostgreSQL integration test for `JdbcEvalCaseRunStore`.
- Restart persistence smoke test.

Add a live repository test later when the project has a standard Testcontainers or integration-test profile.
