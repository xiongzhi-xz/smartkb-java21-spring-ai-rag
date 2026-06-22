# SmartKB

Java 21 + Spring AI 企业智能知识库系统，核心展示目标是一个可本地运行、可演示、可解释的 Advanced RAG 闭环。

SmartKB 的主线能力是：文档上传、解析、切片、Embedding、pgvector 检索、多轮流式问答、Advanced RAG、引用片段定位和 Redis 会话记忆。仓库中也保留了一组面向 Java 项目接管的 Agent 工作台实验代码，但首页默认隐藏这些入口，当前展示主线只围绕 RAG。

## 先看这个：完整演示动图

下面是带字幕的高清自动播放 GIF，覆盖上传、入库、文档详情、普通问答、多轮追问、Advanced RAG、引用跳转和检索质量评测。

![SmartKB RAG 完整演示动图](docs/screenshots/smartkb-rag-demo.gif)

GitHub README 内嵌展示使用 GIF；高清 MP4 可通过链接播放或下载：[smartkb-rag-demo.mp4](docs/screenshots/smartkb-rag-demo.mp4)

## GitHub 展示入口

如果你是第一次打开这个仓库，建议先按这个顺序看：

1. 本页顶部的完整演示动图。
2. 本页的“项目亮点”“架构图”“快速启动”“演示路径”和“验证状态”。
3. [docs/github-showcase.md](docs/github-showcase.md)：快速展示摘要、推荐浏览顺序、项目摘要和边界说明。
4. [DEMO.md](DEMO.md)：本地 5 分钟演示路径。
5. [docs/demo-runbook.md](docs/demo-runbook.md)：设计说明、设计取舍和替代路径。

展示边界：SmartKB 是本地可运行、可演示、可解释的 Java 21 + Spring AI RAG 项目，不声称已经生产商用；生产级 HA、TLS、托管 Secret 和完整多 Agent OS 不在当前范围。
## 项目亮点

- **Advanced RAG 闭环**：文档上传、UTF-8 解析、切片、Ollama Embedding、pgvector 入库、Hybrid Search、查询改写、过滤、重排序和引用片段定位。
- **Redis ChatMemory**：自研实现 Spring AI `ChatMemory` 接口，Redis List + TTL 持久化多轮会话，支持服务重启后恢复上下文。
- **流式对话体验**：普通对话和 Advanced RAG 都支持 SSE 流式返回；Advanced 模式展示查询改写、检索、过滤、重排、生成等阶段反馈。
- **RAG 检索质量评测**：内置 8 个中文评测问题和预期 chunk，对比普通向量召回与 Advanced RAG 的 Recall@K、Top1、MRR 和引用覆盖。
- **可观测性**：Micrometer 自定义 Counter/Timer，Prometheus 指标采集，Grafana Dashboard 预配置。
- **Docker Compose 一键运行**：PostgreSQL + pgvector、Redis、Spring Boot、Prometheus、Grafana 一套 Compose 启动。
- **可选工程工作台实验**：项目接管、任务状态、记忆层、代码上下文和 Eval API 已实现，但首页入口默认隐藏，当前 README 和演示只展示 RAG 主链路。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| Runtime | Java 21, Spring Boot 3.3.1, Virtual Threads |
| AI | Spring AI 1.0.0-M1, OpenAI-compatible Chat API, Ollama `nomic-embed-text` |
| RAG | pgvector, Hybrid Search, Query Rewriting, Metadata Filtering, Re-ranking |
| Storage | PostgreSQL 16, Redis 7 |
| Observability | Spring Boot Actuator, Micrometer, Prometheus, Grafana |
| Delivery | Docker Compose, Docker BuildKit, K3s demo manifest |
| Test | JUnit 5, Mockito, Spring MVC Test, Testcontainers profile |

## 架构图

```mermaid
flowchart LR
    U[Browser Workbench] --> API[Spring Boot API]

    subgraph RAG["RAG Pipeline"]
        API --> Loader[Document Loader]
        Loader --> Splitter[Chunking]
        Splitter --> Emb[Ollama Embedding]
        Emb --> PG[(PostgreSQL + pgvector)]
        API --> Retrieval[Hybrid Retrieval]
        Retrieval --> PG
        Retrieval --> Rerank[Filter + Re-rank]
        Rerank --> Chat[OpenAI-compatible Chat Model]
        Chat --> API
    end

    subgraph Memory["Conversation Memory"]
        API --> Redis[(Redis ChatMemory)]
    end

    subgraph Agent["Optional Engineering Workbench"]
        API --> Intake[Project Intake]
        API --> Task[Agent Task State Machine]
        API --> Code[Code Context Search]
        API --> Eval[Eval Runs + Report]
        Code --> FS[Local Java Project Files]
        Code --> Git[Git Status / Diff / Log]
    end

    API --> Metrics[Micrometer Metrics]
    Metrics --> Prom[Prometheus]
    Prom --> Grafana[Grafana]
```

## 界面截图

| 步骤 | 桌面横屏截图 |
| --- | --- |
| 1. 上传知识文档 | ![SmartKB 上传知识文档](docs/screenshots/desktop/smartkb-01-upload-document.png) |
| 2. 上传后进入知识库 | ![SmartKB 文档入库](docs/screenshots/desktop/smartkb-02-document-indexed.png) |
| 3. 查看文档切片详情 | ![SmartKB 文档切片详情](docs/screenshots/desktop/smartkb-03-document-chunks.png) |
| 4. 普通 RAG 问答 | ![SmartKB 普通 RAG 问答](docs/screenshots/desktop/smartkb-04-normal-rag-qa.png) |
| 5. 多轮追问 | ![SmartKB 多轮追问](docs/screenshots/desktop/smartkb-05-follow-up-chat.png) |
| 6. Advanced RAG 分阶段回答 | ![SmartKB Advanced RAG 分阶段回答](docs/screenshots/desktop/smartkb-06-advanced-rag.png) |
| 7. 点击引用片段定位原文 | ![SmartKB 引用片段定位原文](docs/screenshots/desktop/smartkb-07-citation-jump.png) |
| 8. RAG 检索质量评测报告 | ![SmartKB RAG 检索质量评测报告](docs/screenshots/desktop/smartkb-08-rag-quality-eval.png) |

截图为 `1440x900` 桌面横屏视口，按 RAG 演示主链路依次覆盖：上传中文测试文档、查看入库结果、打开文档 chunk 详情、普通问答、多轮追问、Advanced RAG 分阶段回答、点击引用片段定位到原文 chunk、RAG 检索质量评测报告。

## 功能清单

### RAG 知识库

- 文档上传：Markdown、TXT、PDF、DOCX。
- 文档管理：列表、详情、删除、统计。
- 文档切片可视化：查看入库 chunk，引用片段可定位到文档详情。
- 普通问答：多轮对话、流式输出、会话 ID 管理。
- Advanced RAG：查询改写、双路召回、文档过滤、关键词/锚点重排、阶段耗时指标。
- RAG 检索质量评测：内置中文用例和预期 chunk，对比普通召回和 Advanced RAG 检索链路，统计 Recall@K、Top1、MRR 和引用覆盖。
- Redis 会话记忆：刷新或重启应用后，同一 `conversationId` 可恢复上下文。

### 可选工程工作台实验

这些能力用于探索“AI 如何接管 Java 项目”，不作为当前 5 分钟主演示路径。它们的前端代码和 API 保留，但首页导航默认隐藏，展示重点只放在上面的 RAG 闭环。

- 项目接管：读取 `README/SPEC/AGENTS/HANDOFF/pom.xml/docker-compose.yml/Git` 信息，生成接管摘要和指标速览。
- 任务状态机：`INTAKE -> PLAN -> EXECUTE -> VERIFY -> RECORD`，记录状态流转和验证结果，任务列表提供摘要指标。
- 记忆分层：高权威记忆、中权威记忆、低权威记忆，工作台支持导入、手工新增、列表查看、摘要指标和冲突提示。
- 代码上下文：文件树、关键词搜索、Git diff、代码 chunk、语义检索，并展示结果数、跳过数、警告和 Git 状态。
- Eval 评测：记录 TicketRush eval case，运行列表提供摘要指标，并聚合成功率、得分率、失败原因和人工介入指标。

## 快速启动

### 方式一：Docker Compose 全链路

准备 `.env`：

```bash
cp .env.example .env
```

填入你的 Chat API 配置，示例字段：

```env
TRANSIT_API_KEY=your-chat-api-key
TRANSIT_BASE_URL=https://api.deepseek.com
AI_MODEL=deepseek-chat
SMARTKB_PROJECTS_ROOT=..
```

启动：

```bash
docker compose up -d
```

访问：

| 服务 | 地址 |
| --- | --- |
| SmartKB | http://localhost:8082 |
| Grafana | http://localhost:3001 |
| Health | http://localhost:8082/actuator/health |

Docker 模式下 Project Intake 使用容器路径：

```text
/workspace/projects/<project-dir>
```

例如：

```text
/workspace/projects/smartkb-java21-spring-ai-rag
```

### 方式二：Hybrid 本地开发

只启动 PostgreSQL 和 Redis：

```bash
docker compose -f docker-compose-minimal.yml up -d
```

准备 Ollama Embedding 模型：

```bash
ollama pull nomic-embed-text
```

IDEA 或命令行启动 Spring Boot：

```text
Active profiles: hybrid
Environment variables:
TRANSIT_API_KEY=your-chat-api-key;TRANSIT_BASE_URL=https://api.deepseek.com;AI_MODEL=deepseek-chat
```

访问：

```text
http://localhost:8080
```

完整启动细节见 [STARTUP.md](STARTUP.md)。

## 演示路径

推荐演示文档：

```text
test-docs/advanced-rag-demo.md
```

5 分钟演示：

1. 打开 SmartKB 工作台。
2. 上传 `advanced-rag-demo.md`。
3. 查看文档详情和 chunk。
4. 在“智能问答”中进行多轮流式问答。
5. 切换 Advanced 模式，选择指定文档，提问：

```text
查询改写在 Advanced RAG 中解决什么问题？
为什么引用片段能提升 RAG 系统可信度？
```

6. 展开引用片段并定位到文档详情 chunk。
7. 停在引用片段定位结果，说明 SmartKB 的核心价值是“回答可追溯到原文 chunk”。

详细脚本见 [DEMO.md](DEMO.md)。

## API 概览

### 文档与问答

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/documents/upload` | 上传文档并生成向量 |
| `GET` | `/api/documents` | 文档列表 |
| `GET` | `/api/documents/{fileName}` | 文档详情和 chunks |
| `DELETE` | `/api/documents/{fileName}` | 删除文档和向量 |
| `POST` | `/api/chat/conversation/stream` | 普通多轮流式对话 |
| `POST` | `/api/chat/advanced/stream` | Advanced RAG 分阶段流式回答 |
| `DELETE` | `/api/chat/memory/{conversationId}` | 清理 Redis 会话记忆 |
| `GET` | `/api/rag/eval/cases` | 内置中文 RAG 检索评测集 |
| `POST` | `/api/rag/eval/run` | 运行 RAG 检索质量评测 |
| `GET` | `/api/rag/eval/report` | 默认 RAG 检索评测报告 |

### 可选工程工作台实验

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/agent/projects/intake` | 项目接管摘要 |
| `POST` | `/api/agent/tasks` | 创建 Agent 任务 |
| `POST` | `/api/agent/tasks/{id}/transition` | 状态流转 |
| `GET` | `/api/agent/memories` | 记忆列表 |
| `POST` | `/api/agent/memories` | 创建分层记忆 |
| `POST` | `/api/agent/memories/import/high-authority` | 导入高权威记忆 |
| `POST` | `/api/agent/memories/conflicts/check` | 检查记忆冲突 |
| `POST` | `/api/agent/code/search` | 代码关键词搜索 |
| `POST` | `/api/agent/code/diff` | Git diff 检索 |
| `POST` | `/api/agent/code/semantic` | 语义补充检索 |
| `POST` | `/api/agent/eval/runs` | 创建 Eval Run |
| `GET` | `/api/agent/eval/report` | Eval 聚合报告 |

## 验证状态

当前已验证：

- `mvn test`：115 tests passed。
- Docker Compose 全链路启动：`smartkb-app` healthy。
- Redis ChatMemory live checklist：6/6 通过。
- RAG 检索质量评测：默认 8 个中文用例，覆盖预期 chunk、Recall@K、Top1、MRR、引用覆盖和接口返回结构。
- Docker BuildKit 缓存构建：缓存命中后重建约秒级。
- 可选工程工作台实验：Project Intake 只读挂载、Eval Run 存储和工作台交互已有测试覆盖，但不作为主演示重点。
- 工作台浏览器 smoke：桌面端和 390px 移动视口均覆盖 6 个工作区切换，移动端无横向溢出。
- 移动端表单 smoke：Project Intake、Code Context、AgentTask 和 Eval 均已在 390px 视口通过。
- 移动端边界 smoke：长文本输入、必填错误提示、窄屏按钮/导航宽度已在本地静态页和 Docker 运行态首页通过。
- 工作台摘要指标 smoke：`node .\scripts\smoke\workbench-summary-smoke.mjs` 已覆盖 Project Intake 接管报告、Project Intake / AgentTask / Code Context / Eval 指标渲染和横向溢出检查，并已在 Docker 运行态首页通过。
- K3s demo manifest：已增加 JUnit 结构守卫，并已在一次性 K3d 集群中验证 PostgreSQL、Redis、SmartKB、PVC、health 和 Eval report API。
- K8s draft guard：`K8sDraftManifestTest` 已覆盖 `deployment-draft.yaml` 警告、占位 Secret 和 README 部署入口。

说明：

- Testcontainers 集成测试在部分 Windows Docker Desktop 环境中可能因为 npipe Java Docker client 配置被跳过。
- K3s demo manifest 仍是本地演示形态；生产级 HA、TLS、镜像仓库、监控和托管 Secret 尚未纳入。

## 项目结构

```text
src/main/java/com/smartkb
├── agent                 # SmartKB v2 Agent 工程平台
│   ├── application       # 接管、任务、记忆、代码上下文、Eval 服务
│   ├── controller        # Agent REST API
│   ├── domain            # AgentTask、MemoryRecord、EvalRun 等模型
│   └── infrastructure    # 文件系统与 Git 读取
├── config                # Spring AI、Redis ChatMemory、VectorStore、异常处理
├── controller            # 文档与问答 API
├── domain                # RAG 领域模型
├── service               # 文档加载、RAG、Advanced RAG、指标
└── util                  # Virtual Thread 诊断工具
```

## 设计说明

项目概览：

```text
SmartKB 是我做的 Java 21 + Spring AI 企业知识库项目，主演示是一条完整的 Advanced RAG 工程闭环：上传中文知识文档、切片入库、pgvector 检索、多轮流式问答、Advanced RAG 分阶段回答、引用片段定位到原文 chunk，并用 Redis ChatMemory 保留会话上下文。仓库里还保留了面向 Java 项目接管的工程工作台实验代码，但首页不展示这些入口，它们只作为扩展方向和自测材料。
```

设计取舍：

- 为什么 Redis ChatMemory 比 InMemoryChatMemory 更适合演示分布式和重启恢复？
- Advanced RAG 中查询改写、过滤和重排序分别解决什么问题？
- 为什么引用片段定位比纯聊天回答更适合企业知识库？
- 可选工程工作台为什么需要真实项目和更长操作路径，不能只靠按钮说明价值？
- Java 21 Virtual Threads 在文档解析、Embedding、数据库访问和模型调用这类 IO 密集场景中的价值是什么？

更完整说明方式见 [docs/EVAL_TECHNICAL_SUMMARY.md](docs/EVAL_TECHNICAL_SUMMARY.md) 和 [SPEC.md](SPEC.md)。

## 文档导航

| 文档 | 说明 |
| --- | --- |
| [STARTUP.md](STARTUP.md) | 本地启动指南 |
| [DEMO.md](DEMO.md) | 5 分钟演示脚本 |
| [docs/github-showcase.md](docs/github-showcase.md) | GitHub 展示摘要、浏览顺序和项目摘要 |
| [docs/demo-runbook.md](docs/demo-runbook.md) | 演示 Runbook、设计取舍和替代路径 |
| [TESTING.md](TESTING.md) | 测试指南 |
| [SPEC.md](SPEC.md) | 当前规格、进度和设计说明 |
| [docs/AGENT_PLATFORM_SPEC.md](docs/AGENT_PLATFORM_SPEC.md) | SmartKB v2 Agent 平台规格 |
| [docs/REDIS_CHAT_MEMORY_VERIFICATION.md](docs/REDIS_CHAT_MEMORY_VERIFICATION.md) | Redis 会话记忆验证记录 |
| [docs/PROJECT_INTAKE_API_DESIGN.md](docs/PROJECT_INTAKE_API_DESIGN.md) | Project Intake 设计 |
| [docs/CODE_CONTEXT_API_DESIGN.md](docs/CODE_CONTEXT_API_DESIGN.md) | 代码上下文设计 |
| [docs/EVAL_RUN_PERSISTENCE_DESIGN.md](docs/EVAL_RUN_PERSISTENCE_DESIGN.md) | Eval Run 持久化方案 |
| [docs/K3S_DEPLOYMENT_PLAN.md](docs/K3S_DEPLOYMENT_PLAN.md) | K3s 部署方案 |
| [k8s/README.md](k8s/README.md) | Kubernetes/K3s 清单说明 |

## 安全说明

- `.env` 已加入 `.gitignore`，不要提交真实 API Key。
- `.env.example` 只保留占位字段和公开示例。
- 检查配置时只说明字段是否存在，不复述密钥值。
- Docker Compose 中的数据库账号密码仅用于本地演示环境，不用于生产。
