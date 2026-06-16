# Eval Case Run API Design

Eval Case Run is the first structured record layer for SmartKB v2 evaluation results. The default mode stores runs in memory, and JDBC persistence can be enabled with `smartkb.agent.eval-run.persistence=jdbc`.

## 1. Scope

- Record one execution result for one eval case.
- List runs with optional `projectId`, `caseId`, and `status` filters.
- Read one run by id.
- Keep the API honest about partial and failed runs by supporting `failureReason`.
- Keep controller behavior independent from the active store implementation.

The current implementation supports both in-memory and JDBC-backed stores. See `docs/EVAL_RUN_PERSISTENCE_DESIGN.md` and `docs/EVAL_RUN_JDBC_VERIFICATION.md`.

## 2. API

### Create Eval Run

```http
POST /api/agent/eval/runs
```

Request:

```json
{
  "projectId": "ticket-project",
  "caseId": "E01",
  "title": "TicketRush intake",
  "status": "PASSED",
  "score": 9,
  "maxScore": 10,
  "humanInterventions": 0,
  "toolCallCount": 12,
  "durationSeconds": 180,
  "evidencePaths": [
    "HANDOFF.md",
    "docs/agent-eval-report.md"
  ],
  "verificationCommands": [
    "git status --short",
    "git diff --check"
  ],
  "summary": "TicketRush intake summary and risks recorded.",
  "failureReason": null
}
```

Response:

```json
{
  "id": "generated-run-id",
  "projectId": "ticket-project",
  "caseId": "E01",
  "title": "TicketRush intake",
  "status": "PASSED",
  "score": 9,
  "maxScore": 10,
  "humanInterventions": 0,
  "toolCallCount": 12,
  "durationSeconds": 180,
  "evidencePaths": [
    "HANDOFF.md",
    "docs/agent-eval-report.md"
  ],
  "verificationCommands": [
    "git status --short",
    "git diff --check"
  ],
  "summary": "TicketRush intake summary and risks recorded.",
  "failureReason": null,
  "createdAt": "2026-06-17T04:30:00+08:00"
}
```

### List Eval Runs

```http
GET /api/agent/eval/runs?projectId=ticket-project&caseId=E01&status=PASSED
```

All query parameters are optional. Results are sorted by `createdAt` descending.

### Get Eval Run

```http
GET /api/agent/eval/runs/{id}
```

### Import TicketRush Eval Report

```http
POST /api/agent/eval/runs/import-ticket-rush-report
```

Imports the existing E01-E10 TicketRush records from `docs/agent-eval-report.md` into the active Eval Run store. The import is idempotent per `projectId + caseId`; repeated calls skip records that already exist.

### Generate Eval Report

```http
GET /api/agent/eval/report?projectId=ticket-project
```

`projectId` is optional. The report aggregates the currently recorded runs and returns:

- `totalRuns`, `passedRuns`, `partialRuns`, `failedRuns`
- `successRate`
- `scoreRate`
- `averageDurationSeconds`
- `totalHumanInterventions`
- `totalToolCallCount`
- latest status per eval case
- failure reason distribution

## 3. Validation

- `caseId`, `title`, and `status` are required.
- `status` must be one of `PASSED`, `PARTIAL`, or `FAILED`.
- `score`, `humanInterventions`, `toolCallCount`, and `durationSeconds` must be non-negative when provided.
- `maxScore` must be positive when provided.
- `score` cannot exceed `maxScore`.
- Empty evidence paths and verification commands are trimmed out and duplicates are removed.

## 4. Next Step

Run the JDBC smoke test from `docs/EVAL_RUN_JDBC_VERIFICATION.md` when PostgreSQL is available.
