# SmartKB Enterprise RAG SPEC

## 当前目标

将 SmartKB 从以 `PgVectorStore + Redis ChatMemory` 为中心的演示型 RAG，重构为可在本地 Docker Compose 与 K3s 环境运行的单租户企业知识库服务。重点是文档生命周期、异步入库、可追溯检索、持久化会话和可观测交付，而非仅替换向量数据库。

## 已确认边界

- 单租户，不实现多租户隔离。
- 本地 Ollama 仅用于 Embedding，避免订阅型 Embedding 成本。
- ChatModel 继续使用现有 OpenAI 兼容中转接口；不改为全本地生成模型。
- Redis 保留用于缓存、限流、分布式锁和短期任务状态，不作为长期会话记忆。
- 不在本阶段新增登录、RBAC、计费或生产 Secret 管理。
- 数据库 schema 与迁移脚本在实施持久化任务前单独评审，不在规格阶段修改。

## 目标架构

```text
Browser
  -> REST/SSE API
  -> Application Orchestration
       -> PostgreSQL: knowledge-base/document/chunk metadata/conversation/audit
       -> MinIO: original files
       -> RabbitMQ: ingestion jobs and retry events
       -> Ollama: local embeddings
       -> Milvus: dense-vector retrieval
       -> OpenSearch: keyword retrieval, filters, aggregations
       -> Redis: cache, rate limit, locks, short-lived job status
       -> BGE Reranker: local reranking service
       -> OpenAI-compatible ChatModel: answer generation
  -> OpenTelemetry/Micrometer -> Prometheus -> Grafana
```

## 技术栈

| 领域 | 技术 | 职责 |
| --- | --- | --- |
| Runtime | Java 21, Spring Boot 3.x, Virtual Threads | API、任务编排、IO 并发 |
| AI | Spring AI, Ollama Embedding, OpenAI-compatible ChatModel | 本地向量化与回答生成 |
| Vector Retrieval | Milvus | 向量检索、Collection 生命周期、向量索引 |
| Search | OpenSearch | 全文检索、过滤、聚合和稀疏召回 |
| Business Data | PostgreSQL 16 | 文档元数据、任务、会话、消息、审计记录 |
| File Storage | MinIO | 原始上传文件和可重建来源文件 |
| Messaging | RabbitMQ | 文档入库异步化、重试和死信处理 |
| Cache & Coordination | Redis 7 | 缓存、限流、幂等锁、短期任务状态 |
| Observability | OpenTelemetry, Micrometer, Prometheus, Grafana | Trace、指标、告警基础 |
| Delivery | Docker Compose, K3s | 本地联调与部署演示 |
| Test | JUnit 5, Mockito, Testcontainers | 单元、适配器与集成验证 |

## 核心业务链路

### 文档入库

```text
Upload -> MinIO original file -> PostgreSQL document record (PENDING)
       -> RabbitMQ ingestion job
       -> Parse -> Chunk -> Ollama Embedding
       -> Milvus vectors + OpenSearch documents
       -> PostgreSQL chunk/index status (READY)
```

- 每个入库任务有独立 jobId、状态、重试次数、失败原因和耗时。
- 文档删除、重传和重建索引必须具备幂等性。
- 失败任务进入可查询的失败状态；可由管理员触发重试，不直接丢弃。

### 查询问答

```text
Question + conversationId
  -> PostgreSQL load durable conversation history
  -> rewrite query
  -> Milvus dense retrieval + OpenSearch keyword retrieval
  -> reciprocal-rank fusion + metadata filter
  -> local BGE reranker
  -> evidence confidence gate
  -> ChatModel streaming answer
  -> PostgreSQL persist message, citations, retrieval trace
  -> SSE response
```

- Redis 只缓存近期会话摘要、热点检索结果和限流计数；缓存失效不丢失会话事实。
- 回答、引用、模型调用耗时、检索候选和拒答原因均可追溯。

## 数据模型

| 实体 | 关键字段 | 说明 |
| --- | --- | --- |
| knowledge_base | id, name, status | 单租户下仍保留知识库边界，便于后续扩展 |
| document | id, knowledge_base_id, file_name, object_key, checksum, status | 文档事实与 MinIO 对象关联 |
| document_chunk | id, document_id, ordinal, content_hash, index_status | Chunk 元数据，不存向量 |
| ingestion_job | id, document_id, status, retry_count, error_code, timestamps | 可重试异步入库任务 |
| conversation | id, title, last_message_at, status | 长期会话主体 |
| conversation_message | id, conversation_id, role, content, citations, trace_id | 用户与助手消息审计 |
| retrieval_trace | id, message_id, query, candidates, rerank_mode, latency | 检索和模型调用可解释记录 |

Milvus 与 OpenSearch 文档使用统一的 `chunkId`、`documentId`、`knowledgeBaseId` 和版本号；PostgreSQL 是生命周期与一致性事实来源。

## 模块结构

```text
src/main/java/com/smartkb
  api/                 # REST/SSE controller, request/response DTO
  application/         # upload, ingestion, query, conversation use cases
  domain/              # aggregates, ports, domain events, value objects
  infrastructure/
    persistence/       # PostgreSQL repositories
    objectstorage/     # MinIO adapter
    messaging/         # RabbitMQ publisher/consumer and retry policy
    retrieval/         # Milvus and OpenSearch adapters, fusion
    ai/                # embedding, reranker, chat-model adapters
    cache/             # Redis cache, rate limit, distributed locks
  config/              # Spring configuration and observability
```

现有平铺的 `service`、`config` 和超大 Controller 将逐步迁移，迁移期间通过接口适配保持 API 行为稳定。

## API 设计

- `POST /api/documents`：上传文件，返回 `documentId` 与 `jobId`。
- `GET /api/documents`：分页查询文档与入库状态。
- `GET /api/documents/{documentId}`：文档详情、Chunk 和索引状态。
- `POST /api/documents/{documentId}/retry`：重试失败入库任务。
- `DELETE /api/documents/{documentId}`：删除元数据、对象与检索索引。
- `POST /api/conversations`：创建持久化会话。
- `GET /api/conversations/{conversationId}/messages`：分页读取会话记录。
- `POST /api/chat/stream`：SSE 流式问答，返回阶段、引用、traceId 和结果。
- `GET /api/retrieval-traces/{traceId}`：查询检索证据与耗时。

现有 `/api/chat/**` 和 `/api/rag/eval/**` 在迁移阶段保留兼容层；移除接口前必须有替代接口和回归测试。

## 分阶段任务

- [x] Phase 1a: 引入 Flyway 企业 RAG 核心表，完成 PostgreSQL 持久化会话；Redis 不再承载长期记忆。
- [x] Phase 1b: 建立会话领域端口与 PostgreSQL Repository 适配器。
- [ ] Phase 1c: 建立 Redis 缓存适配层与会话摘要缓存策略。
- [ ] Phase 2: 引入 MinIO、RabbitMQ，完成异步文档入库、状态机、重试和幂等控制。
  - [x] Phase 2a: 完成 MinIO/RabbitMQ 本地配置、扩展入库事件和消费者状态门禁。
  - [x] Phase 2b: 完成上传提交编排、`kb_document`/`ingestion_job` 原子准备、对象保存和事件发布。
  - [ ] Phase 2c: 补齐文档状态查询、删除、失败重试和 Testcontainers 集成验证。
    - [x] Phase 2c-a: 企业文档列表/详情优先读取 `kb_document` 与最新 `ingestion_job`，并保留文件名兼容查询。
    - [x] Phase 2c-b: 增加失败入库任务重试 API，完成 `FAILED -> RETRYING` 保护迁移、对象校验、幂等竞态控制和发布失败补偿。
    - [x] Phase 2c-c: 增加企业文档删除 API，保护活动任务，按索引、MinIO、PostgreSQL 顺序清理并支持失败后重试。
- [ ] Phase 3: 引入 Milvus 与 OpenSearch，完成双路召回、过滤、RRF 融合和索引一致性。
- [ ] Phase 4: 迁移查询编排、引用追溯、SSE 阶段事件和检索 trace。
- [ ] Phase 5: 补齐 Testcontainers 集成测试、故障降级、监控指标和压测报告。
- [ ] Phase 6: Docker Compose/K3s 本地部署验收与项目文档更新。

## 验收标准

- 上传成功后可查询任务状态；重复请求不会产生重复索引数据。
- 任一解析、Embedding、Milvus 或 OpenSearch 失败时，任务状态可追踪、可重试且不污染 READY 数据。
- 文档删除后，PostgreSQL、MinIO、Milvus 和 OpenSearch 的关联数据均最终一致地清理。
- 同一 `conversationId` 在服务重启后可从 PostgreSQL 恢复历史；Redis 不可用不丢失历史消息。
- 检索结果包含来源文档、Chunk、融合与重排信息，并可按 traceId 复盘。
- 本地 Ollama Embedding、当前 ChatModel 中转接口和 Redis 缓存可独立降级，不影响状态一致性。
- 单元测试、适配器集成测试、Docker Compose 配置检查和 JDK 21 构建通过。

## 不做什么

- 不做多租户、RBAC、SSO、计费和企业级 Secret 管理。
- 不将 Redis 作为长期对话记忆或文档事实存储。
- 不再向 pgvector `vector_store` 写入新的生产链路数据。
- 不在未评审迁移与回滚策略前直接删除现有 pgvector 数据或历史 API。
- 不因为本地 Embedding 需求而切换当前 ChatModel 供应商。
