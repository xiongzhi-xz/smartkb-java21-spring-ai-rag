# SmartKB 演示 Runbook

这份文档用于快速熟悉 SmartKB 的本地演示路径。目标不是覆盖所有实现细节，而是稳定说明清楚：为什么做、怎么跑、怎么演示、设计取舍如何解释。

## 一句话定位

SmartKB 是一个 Java 21 + Spring AI 企业知识库项目，当前展示重点是 Advanced RAG 工程闭环。Agent 工程工作台作为扩展实验保留在代码和文档中，但首页默认隐藏入口，不进入默认主演示。

## 快速概览

```text
SmartKB 是我做的 Java 21 + Spring AI 企业知识库项目。主演示覆盖文档上传、Ollama Embedding、pgvector 检索、Advanced RAG、引用片段定位、Redis 会话记忆和 Prometheus/Grafana。仓库中也保留了 Java 项目接管、任务状态、记忆层、代码上下文和 Eval 评测实验代码，但首页默认隐藏它们，不作为 5 分钟展示重点。
```

## 完整概览

```text
这个项目主要解决企业知识库 RAG 如何工程化，而不是只做一个聊天 demo：文档上传后会解析、切片、生成 embedding、写入 pgvector，问答时支持 Hybrid Search、查询改写、文档过滤、重排序和引用片段定位。Agent 工作台是后续方向验证，适合自测和设计讨论，不适合作为默认展示主线，因此不占用首页导航。

技术上我用 Java 21 和 Spring Boot 3 承载后端，用 Spring AI 组织 ChatModel、Embedding 和 VectorStore，用 PostgreSQL + pgvector 做向量库，用 Redis 做会话记忆，配 Prometheus/Grafana 做指标。可选 Agent 实验优先用 README、SPEC、AGENTS、HANDOFF、Git 状态、rg 检索和文件树这类确定性上下文，再用语义检索补充。
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

1. 打开工作台首页，说明本次只演示 RAG 知识库主链路。
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
7. 点击“评测检索质量”，展示固定中文问题集下普通向量召回、Advanced RAG 和引用片段的检索指标对比。
8. 停在引用定位或检索评测结果，说明核心价值是“答案可以回到原文 chunk，并且检索效果可评估”，不是纯聊天输出。

## 如果现场环境不完整

- Chat API key 不可用：不现场生成新回答，改看 README 截图、已有文档详情和引用定位。
- Ollama embedding 不可用：不要现场上传新文档，改讲已有文档和后端设计。
- Docker 端口冲突：SmartKB Docker 默认 `8082`，Hybrid 本地默认 `8080`。
- 数据库里旧文档乱码：删除旧文档后重新上传 `test-docs/advanced-rag-demo.md`。

## 设计取舍

### 为什么不把 Agent 工作台放进主演示？

项目接管、任务状态、记忆层、代码上下文和 Eval 更像工程方向验证，需要真实项目和更长操作路径。5 分钟内强行切过去，观众只能看到按钮和表单，不容易理解价值，也会削弱 RAG 主线。

### Advanced RAG 的查询改写解决什么问题？

用户问题往往短、口语化、缺关键词。查询改写把问题补全为更适合检索的表达，再和原始问题双路召回，能提升命中率，同时保留原问题避免改写跑偏。

### Redis ChatMemory 的价值是什么？

InMemoryChatMemory 重启即丢，多个实例也不共享。Redis List + TTL 可以恢复会话、支持分布式部署，并且保留清晰的数据结构和过期策略。Redis 不可用时降级到内存，保证演示和开发体验。

### Eval Run 证明了什么？

Eval Run 把 Agent 接管能力从“看起来能用”变成可记录、可比较，但它属于后续工程实验：每个 case 有状态、得分、失败原因、人工介入次数和聚合报告，后续功能升级能用同一批样本回归。

### RAG 检索质量评测证明了什么？

RAG 检索质量评测把“Advanced RAG 更好”从主观演示变成可比较指标：同一组固定中文问题分别看普通向量召回和 Advanced RAG 是否召回预期 chunk，并统计 Recall@K、Top1、MRR、引用覆盖和失败原因。它只评检索和引用，不评当前聊天历史，也不评模型生成文本，因此结果更稳定。

### Java 21 Virtual Threads 在这里有什么价值？

文档解析、Embedding 调用、数据库访问、模型调用都偏 IO 密集。Virtual Threads 让代码保持同步写法，同时减少平台线程被阻塞浪费，适合 RAG 服务这种大量等待外部系统的场景。

## 范围边界

- 当前 K3s 是本地 demo manifest，不是生产级 HA 部署。
- SmartKB 是预研和作品项目，不是已经上线的商用多租户知识库。
- Agent 工作台当前只是扩展实验，首页默认隐藏入口，不要作为主演示卖点，也不要说成完整自动开发平台。
- Chat API 和 Embedding 质量会影响 RAG 回答，不要把模型输出稳定性说成完全由代码保证。

## 推荐阅读顺序

```text
README.md
DEMO.md
docs/demo-runbook.md
SPEC.md
HANDOFF.md
docs/AGENT_PLATFORM_SPEC.md
docs/EVAL_TECHNICAL_SUMMARY.md
```

## 验证命令

```powershell
mvn test
node --check .\scripts\smoke\workbench-summary-smoke.mjs
node .\scripts\smoke\workbench-summary-smoke.mjs
git diff --check
```
