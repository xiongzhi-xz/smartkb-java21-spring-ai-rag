# SmartKB 5 分钟演示脚本

这份脚本用于项目展示或自测。SmartKB 不只是“上传文档后提问”的知识库 demo，而是分成两层：

```text
RAG 知识库：文档上传 -> 切片 -> Embedding -> pgvector -> Advanced RAG -> 引用片段
Agent 工程平台：项目接管 -> 任务状态 -> 记忆分层 -> 代码上下文 -> Eval 评测
```

## 演示前检查

Docker Compose 模式推荐：

```powershell
docker compose up -d
Invoke-RestMethod http://localhost:8082/actuator/health
```

打开页面：

```text
http://localhost:8082
```

Hybrid 本地开发模式：

```powershell
docker compose -f docker-compose-minimal.yml up -d
ollama list
```

IDEA 启动：

```text
Active profiles: hybrid
Main class: com.smartkb.SmartKbApplication
Page: http://localhost:8080
```

注意：Active profiles 只填 `hybrid`，不要把 API key 写到 profiles 里。

## 演示材料

RAG 推荐文档：

```text
test-docs/advanced-rag-demo.md
```

Agent 推荐接管项目，Docker 模式路径：

```text
/workspace/projects/ticketrush-java21-high-concurrency
```

本机开发路径按实际仓库位置填写，例如：

```text
E:\project\work\job\ticketrush-java21-high-concurrency
```

## 5 分钟主路径

### 1. 打开工作台

打开 SmartKB 页面后，先确认工作台有多组入口：

- 智能问答
- 项目接管
- 任务状态
- 记忆层
- 代码上下文
- Eval 评测

说明重点：SmartKB 第一层是 RAG 知识库，第二层是面向 Java 存量项目的 Agent 工程平台。

### 2. RAG 知识库：上传和切片

在“智能问答”区域上传：

```text
test-docs/advanced-rag-demo.md
```

观察：

- 文档上传成功
- 文档列表刷新
- 详情里可以看到 chunk 文本
- chunk 可用于后续引用片段追溯

设计说明：上传后，后端会完成文档解析、切片、Embedding 生成和 pgvector 入库。文档处理、数据库访问、Embedding 调用和模型调用都偏 IO 密集，适合 Java 21 Virtual Threads。

### 3. RAG 知识库：普通问答和 Advanced RAG

普通问答示例：

```text
Java 21 Virtual Threads 适合解决什么问题？
```

Advanced RAG 示例：切换 Advanced 模式，选择刚上传的文档，依次提问：

```text
查询改写在 Advanced RAG 中解决什么问题？
为什么引用片段能提升 RAG 系统可信度？
```

观察：

- 普通问答支持流式输出
- Advanced RAG 展示查询改写、检索、过滤、重排序、生成等阶段
- 回答下方可以展开引用片段
- 引用片段可以定位到具体 chunk

设计说明：Advanced RAG 不只是把问题丢给模型，而是通过查询改写、双路召回、文档过滤、重排序和引用片段增强回答的可追溯性。

### 4. Agent 工程平台：项目接管

切到“项目接管”，输入 TicketRush 项目路径。

Docker 模式：

```text
/workspace/projects/ticketrush-java21-high-concurrency
```

运行 Project Intake 后观察：

- 当前目标
- 当前阶段
- 已完成
- 未完成
- 工作区状态
- 下一步只做
- 技术栈、可运行命令、验证缺口和风险提示

设计说明：项目接管优先读取 README、SPEC、AGENTS、HANDOFF、pom.xml、Git 状态和目录结构，用确定性上下文生成接管报告，不只依赖向量检索。

### 5. Agent 工程平台：任务、记忆、代码上下文、Eval

继续切换工作区：

- “任务状态”：展示 AgentTask 从 `INTAKE -> PLAN -> EXECUTE -> VERIFY -> RECORD` 的状态流转。
- “记忆层”：展示高权威记忆来自 SPEC/HANDOFF，低权威记忆不能覆盖高权威约束。
- “代码上下文”：展示文件树、关键词检索、Git diff 和代码 chunk；语义检索只做补充。
- “Eval 评测”：展示 TicketRush E01-E10 样本和聚合报告，用于记录接管能力、失败原因、得分和人工介入次数。

设计说明：这一层把 SmartKB 从“知识库问答”扩展到“可接管真实 Java 项目的工程辅助平台”。

## API 验证入口

文档与问答：

```text
POST /api/documents/upload
GET  /api/documents
POST /api/chat/conversation/stream
POST /api/chat/advanced/stream
DELETE /api/chat/memory/{conversationId}
```

Agent 平台：

```text
POST /api/agent/projects/intake
POST /api/agent/tasks
POST /api/agent/tasks/{id}/transition
GET  /api/agent/memories
POST /api/agent/code/search
POST /api/agent/code/diff
POST /api/agent/code/semantic
POST /api/agent/eval/runs
GET  /api/agent/eval/report
```

## 环境不完整时的替代路径

- Chat API key 不可用：跳过 RAG 生成，演示 Project Intake、AgentTask、Memory、Code Context、Eval。
- Ollama Embedding 不可用：不现场上传新文档，改看已有文档和 Agent 工作台。
- Docker 端口冲突：Docker 默认 `8082`，Hybrid 本地默认 `8080`。
- 数据库里旧文档乱码：删除旧文档后重新上传 `test-docs/advanced-rag-demo.md`。

## 设计取舍

### 为什么不是纯聊天页面？

企业知识库的关键不是让模型自由回答，而是把文档解析、切片、检索、引用片段、会话记忆和质量验证做成可解释的工程闭环。

### 为什么 Agent 接管不只用向量检索？

代码项目接管需要确定性证据。README、SPEC、AGENTS、HANDOFF、Git diff、文件树和 `rg` 结果更容易解释来源，向量检索适合作为语义补充。

### Eval Run 的价值是什么？

Eval Run 把“接管能力”记录成可比较的数据：每个 case 有状态、得分、失败原因、人工介入次数和聚合报告，后续改动可以回归验证。

## 验证命令

```powershell
mvn test
node --check .\scripts\smoke\workbench-summary-smoke.mjs
node .\scripts\smoke\workbench-summary-smoke.mjs
git diff --check
```

完整设计和更多演示细节见：

```text
README.md
docs/demo-runbook.md
docs/AGENT_PLATFORM_SPEC.md
docs/EVAL_TECHNICAL_SUMMARY.md
```