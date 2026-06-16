# Memory Record API Design

Memory Record 是 SmartKB v2 的记忆分层能力。第一版先使用内存存储，目标是把长期记忆按权威等级、来源和项目归属管理起来。

## 1. 权威等级

- `HIGH`：来自 `SPEC.md`、`AGENTS.md`、`HANDOFF.md`、架构决策、用户明确确认的规则。
- `MEDIUM`：来自任务完成记录、验证结果、踩坑记录、评审结论。
- `LOW`：来自聊天摘要、Agent 推断、未验证观察。

冲突处理原则：

- 高权威记忆优先于中/低权威记忆。
- 低权威记忆不能直接覆盖项目规则。
- 修改高权威记忆需要记录来源和原因。

## 2. API

### 创建记忆

```http
POST /api/agent/memories
```

请求：

```json
{
  "projectId": "ticket-project",
  "authorityLevel": "HIGH",
  "sourceType": "SPEC",
  "sourcePath": "SPEC.md",
  "content": "Do not expand TicketRush scope before k6 benchmark data exists.",
  "tags": ["scope", "benchmark"]
}
```

### 查询记忆

```http
GET /api/agent/memories
GET /api/agent/memories/{id}
```

支持筛选参数：

- `projectId`
- `authorityLevel`
- `sourceType`

示例：

```http
GET /api/agent/memories?projectId=ticket-project&authorityLevel=HIGH&sourceType=SPEC
```

### 导入高权威记忆

```http
POST /api/agent/memories/import/high-authority
```

请求：

```json
{
  "projectId": "ticket-project",
  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
  "maxFileBytes": 65536
}
```

第一版只读取项目根目录下的 `SPEC.md` 和 `HANDOFF.md`，导入为 `HIGH` 权威记忆，并返回 `imported` 与 `skippedFiles`。

### 检查记忆冲突

```http
POST /api/agent/memories/conflicts/check
```

请求：

```json
{
  "projectId": "ticket-project",
  "authorityLevel": "LOW",
  "content": "Maybe add a new feature now.",
  "tags": ["scope"]
}
```

规则：

- 只检查同项目记忆。
- 标签重叠才认为可能冲突。
- 已有更高权威等级的记忆优先。
- 返回 `preferredMemory` 和 `recommendation`，用于解释为什么采用高权威来源。

## 3. 错误响应

错误沿用全局格式：

```json
{
  "success": false,
  "code": "MEMORY_CONTENT_REQUIRED",
  "error": "content is required"
}
```

常见错误码：

- `MEMORY_CONTENT_REQUIRED`
- `MEMORY_ID_REQUIRED`
- `MEMORY_NOT_FOUND`

## 4. 已验证

- `mvn -Dtest=MemoryRecordServiceTest,MemoryRecordControllerTest test`：9 tests, 0 failures。
- `mvn test`：42 tests, 0 failures。
- `git diff --check`：通过。
- 本地端到端联调：`POST /api/agent/memories` 创建 HIGH 记忆成功。
- 筛选联调：`GET /api/agent/memories?projectId=ticket-project&authorityLevel=HIGH&sourceType=SPEC` 返回刚创建的记忆。
- 高权威导入定向测试：`mvn -Dtest=MemoryRecordServiceTest,MemoryRecordControllerTest,HighAuthorityMemoryImportServiceTest test`：13 tests, 0 failures。
- TicketRush 导入联调：`POST /api/agent/memories/import/high-authority` 导入 `SPEC.md` 和 `HANDOFF.md`，`skippedFiles` 为空。
- 冲突提示定向测试：`mvn -Dtest=MemoryRecordServiceTest,MemoryRecordControllerTest,HighAuthorityMemoryImportServiceTest,MemoryConflictServiceTest test`：17 tests, 0 failures。
- 冲突提示联调：创建 `HIGH`/`SPEC.md` 记忆后，用 `LOW` 同标签输入检查，返回 `hasConflict: true`，推荐采用 HIGH 记忆。
