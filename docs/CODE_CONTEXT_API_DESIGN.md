# Code Context API Design

Code Context is the first read-only code retrieval layer for SmartKB v2. The first version exposes a safe file tree index and keyword search results with source paths and line numbers.

## 1. Safety Rules

- Only read files under the requested project root.
- Reuse `ProjectPathGuard` before any project file access.
- Skip `.git`, `target`, `build`, `node_modules`, runtime data, logs, `.env*`, keys, certificates, and keystores.
- Do not write to the target project.
- Return paths relative to the project root.

## 2. API

### Build File Tree

```http
POST /api/agent/code/tree
```

Request:

```json
{
  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
  "maxFiles": 500,
  "maxDepth": 8
}
```

### Keyword Search

```http
POST /api/agent/code/search
```

Request:

```json
{
  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
  "query": "TicketService",
  "maxResults": 100,
  "maxFileBytes": 1048576
}
```

Search responses return `path`, `lineNumber`, and `line` for each match.

## 3. Errors

Errors use the global response shape:

```json
{
  "success": false,
  "code": "CODE_SEARCH_QUERY_REQUIRED",
  "error": "query is required"
}
```

Common codes:

- `PROJECT_PATH_REQUIRED`
- `PROJECT_PATH_NOT_FOUND`
- `PROJECT_PATH_NOT_DIRECTORY`
- `PROJECT_PATH_NOT_ALLOWED`
- `CODE_SEARCH_QUERY_REQUIRED`

## 4. Verified

- Service tests cover safe tree indexing, keyword matches with line numbers, and blank query rejection.
- Web tests cover `/api/agent/code/tree`, `/api/agent/code/search`, and Code Context error response.
- `mvn -Dtest=CodeContextServiceTest,CodeContextControllerTest test`: 6 tests, 0 failures.
- `mvn test`: 48 tests, 0 failures.
