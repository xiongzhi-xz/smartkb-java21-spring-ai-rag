# SmartKB 5 分钟演示脚本

这份脚本用于项目展示或自测。默认 5 分钟演示只展示 SmartKB 的 RAG 主链路，确保观众能看到一个完整、可解释的知识库闭环：

```text
RAG 知识库：文档上传 -> 切片 -> Embedding -> pgvector -> Advanced RAG -> 引用片段
```

仓库中保留的项目接管、任务状态、记忆层、代码上下文和 Eval 属于可选工程工作台实验，首页默认隐藏这些入口，不放进默认主演示。

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

可选工程工作台实验样本，Docker 模式路径：

```text
/workspace/projects/ticketrush-java21-high-concurrency
```

本机开发路径按实际仓库位置填写，例如：

```text
E:\project\work\job\ticketrush-java21-high-concurrency
```

## 5 分钟主路径

### 1. 打开工作台

打开 SmartKB 页面后，默认停留在“智能问答”。本次演示只围绕这个入口展开，工程工作台实验入口已从首页导航隐藏。

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

### 4. 引用片段定位

在 Advanced RAG 回答下方展开引用片段，点击其中一条引用。

观察：

- 右侧打开文档详情
- 页面自动定位到对应 chunk
- 目标 chunk 有高亮边框

设计说明：这是 SmartKB 区别于普通聊天页的关键闭环：回答不是孤立文本，而是可以追溯到入库文档的具体片段。

### 5. RAG 质量评测

点击右上角“质量评测”。

观察：

- 页面显示内置中文评测用例数量
- 对比普通向量召回和 Advanced RAG 命中情况
- 展示引用片段命中率和 Advanced 相对普通召回的提升用例

设计说明：这一步回答“怎么证明 RAG 效果”的问题。第一版评测只评估检索和引用片段，不把 LLM 生成文本纳入评分，避免模型输出随机性影响质量指标。

## 可选工程工作台实验

以下功能的代码和 API 保留用于自测或后续研究，但首页默认不展示入口，也不建议放进 5 分钟主演示：

- “项目接管”：读取 README、SPEC、AGENTS、HANDOFF、pom.xml 和 Git 状态，生成接管摘要。
- “任务状态”：记录 `INTAKE -> PLAN -> EXECUTE -> VERIFY -> RECORD` 的任务状态流转。
- “记忆层”：导入或手工维护项目级记忆，并做简单冲突提示。
- “代码上下文”：做文件树、关键词、Git diff、代码 chunk 和语义补充检索。
- “Eval 评测”：记录接管样本的状态、得分、失败原因和人工介入次数。

这些功能的价值更偏工程实验，目前页面按钮和单次操作不容易让观众快速理解，因此默认不作为展示重点。

## API 验证入口

文档与问答：

```text
POST /api/documents/upload
GET  /api/documents
POST /api/chat/conversation/stream
POST /api/chat/advanced/stream
DELETE /api/chat/memory/{conversationId}
```

可选工程工作台实验：

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

- Chat API key 不可用：不现场生成新回答，改看已准备好的 RAG 截图和已有文档详情。
- Ollama Embedding 不可用：不现场上传新文档，改看已入库文档、chunk 详情和引用定位。
- Docker 端口冲突：Docker 默认 `8082`，Hybrid 本地默认 `8080`。
- 数据库里旧文档乱码：删除旧文档后重新上传 `test-docs/advanced-rag-demo.md`。

## 设计取舍

### 为什么不是纯聊天页面？

企业知识库的关键不是让模型自由回答，而是把文档解析、切片、检索、引用片段、会话记忆和质量验证做成可解释的工程闭环。

### 可选工程工作台为什么不放进主演示？

它更像一个后续方向验证：项目接管、任务状态、记忆层、代码上下文和 Eval 都需要真实项目和更长操作路径才能看出价值。5 分钟展示里强行切换这些页面，观众只能看到按钮和表单，反而会削弱 RAG 主线。

### Eval Run 的价值是什么？

Eval Run 的定位是后续工程实验的回归记录，不是知识库用户的核心功能。它把接管任务记录成可比较的数据：每个 case 有状态、得分、失败原因、人工介入次数和聚合报告。

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
