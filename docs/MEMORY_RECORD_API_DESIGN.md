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
- `mvn test`：34 tests, 0 failures。
- `git diff --check`：通过。
- 本地端到端联调：`POST /api/agent/memories` 创建 HIGH 记忆成功。
- 筛选联调：`GET /api/agent/memories?projectId=ticket-project&authorityLevel=HIGH&sourceType=SPEC` 返回刚创建的记忆。
