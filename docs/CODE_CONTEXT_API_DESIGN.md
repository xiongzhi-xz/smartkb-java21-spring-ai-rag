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

### Git Diff Search

```http
POST /api/agent/code/diff
```

Request:

```json
{
  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
  "query": "reserveTicket",
  "maxLines": 200
}
```

Diff responses return changed files and diff lines with `type`, `oldLineNumber`, `newLineNumber`, and `content`. The API reads both unstaged and staged diffs, and filters sensitive files before reading diff content.

### Build Code Chunks

```http
POST /api/agent/code/chunks
```

Request:

```json
{
  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
  "maxChunks": 100,
  "maxFileBytes": 1048576,
  "maxChunkChars": 2000
}
```

Chunk responses return safe code snippets with `path`, `startLine`, `endLine`, and `content`. This is the first step toward RAG semantic supplementation: it prepares deterministic, source-linked chunks without writing to the vector store.

### Semantic Code Search

```http
POST /api/agent/code/semantic
```

Request:

```json
{
  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
  "query": "reserve ticket inventory",
  "maxResults": 20,
  "maxFileBytes": 1048576,
  "maxChunkChars": 2000
}
```

The first version uses safe code chunks and deterministic query term ranking. It returns source-linked chunks with `score` and `matchedTerms`, and does not write to the vector store.

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

- Service tests cover safe tree indexing, keyword matches with line numbers, Git diff evidence, code chunk extraction, semantic chunk ranking, sensitive file skipping, and blank query rejection.
- Web tests cover `/api/agent/code/tree`, `/api/agent/code/search`, `/api/agent/code/diff`, `/api/agent/code/chunks`, `/api/agent/code/semantic`, and Code Context error response.
- `mvn -Dtest=CodeContextServiceTest,CodeContextControllerTest test`: 12 tests, 0 failures.
- `mvn test`: 54 tests, 0 failures.
- Static frontend panel syntax check: inline script syntax ok.
