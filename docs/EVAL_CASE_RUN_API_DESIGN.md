# Eval Case Run API Design

Eval Case Run is the first structured record layer for SmartKB v2 evaluation results. The first version stores runs in memory and focuses on recording what happened, how it was verified, and where the evidence lives.

## 1. Scope

- Record one execution result for one eval case.
- List runs with optional `projectId`, `caseId`, and `status` filters.
- Read one run by id.
- Keep the API honest about partial and failed runs by supporting `failureReason`.

This version does not persist runs to a database and does not generate aggregate reports yet.

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

## 3. Validation

- `caseId`, `title`, and `status` are required.
- `status` must be one of `PASSED`, `PARTIAL`, or `FAILED`.
- `score`, `humanInterventions`, `toolCallCount`, and `durationSeconds` must be non-negative when provided.
- `maxScore` must be positive when provided.
- `score` cannot exceed `maxScore`.
- Empty evidence paths and verification commands are trimmed out and duplicates are removed.

## 4. Next Step

Use these run records to build a report aggregation API with success rate, failure reason distribution, total runs, average score, and average duration.
