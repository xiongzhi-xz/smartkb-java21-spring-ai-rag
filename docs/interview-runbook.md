# SmartKB 面试演示 Runbook

这份文档用于明天快速熟悉 SmartKB。目标不是背所有实现细节，而是能稳定讲清楚：为什么做、怎么跑、怎么演示、遇到追问怎么答。

## 一句话定位

SmartKB 是一个 Java 21 + Spring AI 企业知识库项目，第一阶段完成 Advanced RAG 工程闭环，第二阶段升级为面向 Java 存量项目的 Agent 工程平台。

## 30 秒版本

```text
SmartKB 是我做的 Java 21 + Spring AI 企业知识库项目。第一阶段覆盖文档上传、Ollama Embedding、pgvector 检索、Advanced RAG、引用片段、Redis 会话记忆和 Prometheus/Grafana。第二阶段我把它升级成 Java 项目的 Agent 工程平台，能做项目接管、任务状态流转、记忆分层、代码上下文检索和 Eval 评测，并用 TicketRush 作为真实复杂 Java 项目样本验证。
```

## 2 分钟版本

```text
这个项目解决两个问题。第一个是企业知识库 RAG 如何工程化，而不是只做一个聊天 demo：文档上传后会解析、切片、生成 embedding、写入 pgvector，问答时支持 Hybrid Search、查询改写、文档过滤、重排序和引用片段定位。第二个是 AI 工程工具如何接管真实 Java 项目：SmartKB v2 增加了 Project Intake、AgentTask 状态机、MemoryRecord 分层记忆、Code Context 检索和 Eval Run 评测。

技术上我用 Java 21 和 Spring Boot 3 承载后端，用 Spring AI 组织 ChatModel、Embedding 和 VectorStore，用 PostgreSQL + pgvector 做向量库，用 Redis 做会话记忆，配 Prometheus/Grafana 做指标。Agent 部分优先用 README、SPEC、AGENTS、HANDOFF、Git 状态、rg 检索和文件树这类确定性上下文，再用语义检索补充，避免只靠向量检索导致接管结果不稳定。
```

## 演示前检查

Docker 模式推荐：

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

## 5 分钟演示路径

1. 打开工作台首页，先说项目分两层：RAG 知识库 + Agent 工程平台。
2. 上传 `test-docs/advanced-rag-demo.md`，展示文档解析、切片、向量入库。
3. 打开文档详情，展示 chunk 和引用片段可解释性。
4. 在智能问答里提问：

   ```text
   Java 21 Virtual Threads 适合解决什么问题？
   ```

5. 切换 Advanced RAG，选择上传文档，提问：

   ```text
   查询改写在 Advanced RAG 中解决什么问题？
   为什么引用片段能提升 RAG 系统可信度？
   ```

6. 展开引用片段，说明答案能追溯到具体 chunk，不是纯模型自由发挥。
7. 切到“项目接管”，Docker 模式输入：

   ```text
   /workspace/projects/ticketrush-java21-high-concurrency
   ```

8. 展示接管报告六行：当前目标、当前阶段、已完成、未完成、工作区状态、下一步只做。
9. 切到“任务状态”，展示 AgentTask 从 `INTAKE -> PLAN -> EXECUTE -> VERIFY -> RECORD` 的思路。
10. 切到“记忆层”，说明高权威记忆来自 SPEC/HANDOFF，低权威记忆不能覆盖高权威约束。
11. 切到“代码上下文”，说明优先使用 `rg`、Git diff、文件树，语义检索只做补充。
12. 切到“Eval 评测”，说明用 TicketRush 的 E01-E10 作为真实复杂项目样本来测接管能力。

## 如果现场环境不完整

- Chat API key 不可用：演示 Project Intake、AgentTask、Memory、Code Context、Eval，这些本地功能仍能说明 Agent 平台能力。
- Ollama embedding 不可用：不要现场调 RAG 上传，改讲已有文档和后端设计；演示 Agent 平台。
- Docker 端口冲突：SmartKB Docker 默认 `8082`，Hybrid 本地默认 `8080`。
- 数据库里旧文档乱码：删除旧文档后重新上传 `test-docs/advanced-rag-demo.md`。

## 高频追问

### 为什么不用纯向量检索做项目接管？

代码接管更看重确定性证据。README、SPEC、AGENTS、HANDOFF、Git diff、文件树和 `rg` 结果能解释来源，也能避免向量召回漏掉关键约束。向量检索适合补充语义相近内容，但不适合当唯一依据。

### Advanced RAG 的查询改写解决什么问题？

用户问题往往短、口语化、缺关键词。查询改写把问题补全为更适合检索的表达，再和原始问题双路召回，能提升命中率，同时保留原问题避免改写跑偏。

### Redis ChatMemory 的价值是什么？

InMemoryChatMemory 重启即丢，多个实例也不共享。Redis List + TTL 可以恢复会话、支持分布式部署，并且保留清晰的数据结构和过期策略。Redis 不可用时降级到内存，保证演示和开发体验。

### Eval Run 证明了什么？

Eval Run 把 Agent 接管能力从“看起来能用”变成可记录、可比较：每个 case 有状态、得分、失败原因、人工介入次数和聚合报告，后续功能升级能用同一批样本回归。

### Java 21 Virtual Threads 在这里有什么价值？

文档解析、Embedding 调用、数据库访问、模型调用都偏 IO 密集。Virtual Threads 让代码保持同步写法，同时减少平台线程被阻塞浪费，适合 RAG 服务这种大量等待外部系统的场景。

## 不要过度承诺

- 当前 K3s 是本地 demo manifest，不是生产级 HA 部署。
- SmartKB 是预研和作品项目，不是已经上线的商用多租户知识库。
- Agent 平台当前强调接管、检索、状态机和 eval，不要说成完整自动开发平台。
- Chat API 和 Embedding 质量会影响 RAG 回答，不要把模型输出稳定性说成完全由代码保证。

## 明天优先阅读

```text
README.md
DEMO.md
docs/interview-runbook.md
SPEC.md
HANDOFF.md
docs/AGENT_PLATFORM_SPEC.md
docs/EVAL_INTERVIEW_SUMMARY.md
```

## 验证命令

```powershell
mvn test
node --check .\scripts\smoke\workbench-summary-smoke.mjs
node .\scripts\smoke\workbench-summary-smoke.mjs
git diff --check
```
