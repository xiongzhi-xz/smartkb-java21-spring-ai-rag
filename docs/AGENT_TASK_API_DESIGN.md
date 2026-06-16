# Agent Task API Design

Agent Task 是 SmartKB v2 的任务状态机能力。第一版先使用内存存储，目标是把 Agent 任务固定在可验证的生命周期里，避免直接自由发挥。

## 1. 状态流转

固定主路径：

```text
INTAKE -> PLAN -> EXECUTE -> VERIFY -> RECORD
```

终态：

- `RECORD`：验证通过后完成记录。
- `BLOCKED`：无法继续，需要外部条件或用户输入。
- `FAILED`：验证失败或执行失败。

约束：

- 不能跳过状态，例如 `INTAKE -> EXECUTE` 会失败。
- `RECORD` 必须从 `VERIFY` 进入。
- `RECORD` 必须带 `verificationPassed: true`。
- `RECORD`、`BLOCKED`、`FAILED` 进入后不能再流转。

## 2. API

### 创建任务

```http
POST /api/agent/tasks
```

请求：

```json
{
  "projectId": "ticket-project",
  "title": "Run k6 benchmark",
  "goal": "Run first local benchmark",
  "riskLevel": "low"
}
```

响应状态默认为 `INTAKE`，并带一条 `Task created` 事件。

### 查询任务

```http
GET /api/agent/tasks
GET /api/agent/tasks/{id}
```

### 流转任务

```http
POST /api/agent/tasks/{id}/transition
```

请求：

```json
{
  "targetStatus": "PLAN",
  "note": "Plan finished",
  "plan": "Run k6 only",
  "verification": "mvn test passed",
  "resultSummary": "Benchmark flow recorded",
  "verificationPassed": true
}
```

## 3. 错误响应

错误沿用全局格式：

```json
{
  "success": false,
  "code": "TASK_VERIFICATION_REQUIRED",
  "error": "verificationPassed=true is required before RECORD"
}
```

常见错误码：

- `TASK_TITLE_REQUIRED`
- `TASK_GOAL_REQUIRED`
- `TASK_TARGET_STATUS_REQUIRED`
- `TASK_NOT_FOUND`
- `TASK_INVALID_TRANSITION`
- `TASK_VERIFICATION_REQUIRED`
- `TASK_TERMINAL_STATUS`

## 4. 已验证

- `mvn -Dtest=AgentTaskServiceTest,AgentTaskControllerTest test`：9 tests, 0 failures。
- `mvn test`：25 tests, 0 failures。
- `git diff --check`：通过。
- 本地端到端联调：`POST /api/agent/tasks` 创建任务后，HTTP 流转 `INTAKE -> PLAN -> EXECUTE -> VERIFY -> RECORD` 成功。
- 失败保护：`VERIFY -> RECORD` 且 `verificationPassed:false` 返回 `409 TASK_VERIFICATION_REQUIRED`。
