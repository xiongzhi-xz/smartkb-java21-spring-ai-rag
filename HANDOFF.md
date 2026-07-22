# SmartKB Handoff

## Current State (2026-07-22)

- Phase 2a and 2b are implemented: MinIO/RabbitMQ configuration, repeatable object-storage consumption, upload submission orchestration, `kb_document`/`ingestion_job` atomic preparation, object persistence, and event publication.
- `POST /api/documents/upload` now returns `202 Accepted` with `documentId`, `jobId`, `status`, and `queued`; duplicate content reuses the checksum-keyed document/task and does not create a second indexing task.
- Consumer transitions update both `ingestion_job` and `kb_document` together with guarded `PROCESSING -> READY/FAILED` state changes.
- Document list/detail reads now use `kb_document` and the latest `ingestion_job` first; UUID detail lookup exposes lifecycle/task fields, while the legacy file-name route and vector-store chunk content remain compatible.
- `POST /api/documents/{documentId}/retry` now retries only `FAILED` jobs: it verifies the MinIO object, atomically advances the job to `RETRYING`, increments `retry_count`, clears stale errors, and republishes the existing object event.
- Retry requests are idempotent under races: a request that observes an existing `RETRYING` job does not publish a duplicate event. RabbitMQ publication failures are compensated back to `FAILED` with `RETRY_PUBLISH_FAILED`.
- `DELETE /api/documents/{documentId}` now deletes only stable `READY`/`FAILED` documents, locks the document and latest job, clears current pgvector chunks, removes the MinIO object, and cascades PostgreSQL facts; active jobs return `409` and failed external cleanup leaves the fact retryable.
- The static workbench now passes enterprise `documentId` to the delete endpoint while preserving filename deletion for legacy vector_store-only documents.
- `JdbcIngestionJobRepository` uses `ON CONFLICT DO NOTHING` so idempotent preparation remains safe inside a transaction under concurrent duplicate uploads.
- Verification passed locally: `mvn -B test` (81 tests) and `git diff --check`.
- The same 81 tests passed in `maven:3.9-eclipse-temurin-21` (Temurin JDK 21.0.11).
- Added `EnterpriseRagPersistenceIT`: it migrates a PostgreSQL Testcontainers database and verifies document/job idempotency, duplicate-consumer guards, failed-job retry, and stable-state deletion.
- PostgreSQL Testcontainers verification passed in `maven:3.9-eclipse-temurin-21`: all 81 unit tests and 2 integration tests passed. Testcontainers started PostgreSQL 16.14 and Flyway applied V1/V2 successfully.
- Docker Desktop 29 requires the documented Docker-socket container command with `-Dapi.version=1.40`; the native Windows Testcontainers client defaults to an unsupported Docker API version.
- Real PostgreSQL/MinIO/RabbitMQ end-to-end verification is still pending for this retry stage.
- Phase 2 is complete. The next independent module is Phase 3: Milvus/OpenSearch dual retrieval and index consistency.
- Phase 3 design is now recorded in `docs/PHASE3_RETRIEVAL_DESIGN.md`. It fixes the chunk identity contract, both-index `READY` gate, deletion order, weighted RRF contract, and single-backend degradation behavior without changing schema or runtime dependencies.
- The next focused implementation task is Phase 3a: add domain records and outbound index ports with unit tests. Do not add Milvus/OpenSearch dependencies or change migrations until that task is underway and the existing adapter style has been followed.
- Phase 3a is complete: `IndexableChunk`, `RetrievalRequest`, and `RetrievalCandidate` define immutable shared contracts; `DenseVectorIndex` and `KeywordIndex` isolate the future Milvus/OpenSearch adapters. `chunkId` is the stable upsert key. `mvn -B test` passed with 86 tests.
- The next focused implementation task is Phase 3b: add Milvus/OpenSearch Docker Compose services, configuration properties, health checks, and adapter dependencies. Do not change the PostgreSQL migration scripts in that task.
- Phase 3b is complete: Docker Compose now declares standalone Milvus 2.4.8 and OpenSearch 2.18.0 with persistent volumes and health checks. The application has `smartkb.retrieval` properties, Milvus/OpenSearch client beans, and small availability gateways. The Milvus SDK excludes unused bulk-import and cloud-storage dependencies to keep the online RAG runtime focused. `docker compose config --quiet` and `mvn -B -o test` passed with 87 tests.
- The next focused implementation task is Phase 3c: persist deterministic chunk facts, write both indexes before the existing READY transition, and add duplicate-event/failure tests. This requires a separately reviewed PostgreSQL migration before changing `document_chunk` facts.
- The user explicitly authorized the Phase 3c migration on 2026-07-22. The current ingestion listener still calls `RagService.addDocument`, which parses, chunks, embeds, and writes only the compatibility `PgVectorStore` path before calling `markReady`. Phase 3c must instead expose deterministic parsed chunks, persist/verify `document_chunk` facts, write both target indexes, and call `markReady` only after both writes succeed. Keep historical `vector_store` data untouched.
- Phase 3c is complete: `V3__document_chunk_index_status_constraint.sql` constrains chunk state; `DocumentIndexingService` deterministically derives each chunk ID from `(documentId, ordinal)`, persists/verifies its SHA-256 fact, then writes Milvus and OpenSearch before marking chunks and the ingestion job READY. The enterprise ingestion listener no longer invokes the compatibility `RagService`/`PgVectorStore` path. Index failures are recorded as `MILVUS_INDEX_FAILED`, `OPENSEARCH_INDEX_FAILED`, or `INDEX_FINALIZATION_FAILED`; duplicate delivery remains guarded by the existing job claim. `mvn -B test` passed with 88 tests and `git diff --check` passed.
- The next focused implementation task is Phase 3d: parallel dense/keyword retrieval, metadata filters, weighted RRF, and single-backend degradation. Do not extend the deletion flow until Phase 3e.
- Next conversation: first read `SPEC.md`, this handoff, `git status`, and the focused files `RagService`, `DocumentLoaderService`, `EmbeddingService`, `IngestionJobListener`, `JdbcDocumentIngestionRepository`, and the Phase 3 ports. Then propose the minimal V3 migration (chunk content is not required in PostgreSQL; stable ID, ordinal, content hash, and status are), implement the dual-index adapters against the existing clients, and extend listener/repository tests for duplicate events and a failed target-index write. Required verification: `mvn -B test`, `git diff --check`, and, if Docker resources permit, a Compose-backed Milvus/OpenSearch smoke test.
- The historical environment notes below are retained only as context.

## 2026-07-21 提交环境说明

- `docker-compose.yml` 与 `src/main/resources/application.yml` 已补充本地 RabbitMQ 运行配置：发布确认、死信策略、服务健康检查和应用容器连接配置。
- 上述两个配置文件及本交接记录尚未提交；`git diff --check` 通过。
- 当前会话的提权审批服务返回 404，导致 Docker Compose 校验、`git add` 和 `git commit` 都未实际执行。
- 下一会话先执行 `docker compose config --quiet`，再提交：`chore: 配置本地 RabbitMQ 服务`。

## 2026-07-21 企业 RAG Phase 1a

- 已引入 Flyway，并在本地 PostgreSQL 16 上实际执行 `V1__enterprise_rag_core.sql`。
- 已创建知识库、文档、Chunk、入库任务、会话、消息和检索 trace 共 7 张核心表。
- 原 `vector_store` 表已确认保留，未被迁移修改或删除。
- Spring AI ChatMemory 已从 Redis List 切换为 PostgreSQL 持久化实现，使用原子会话序号保证并发写入顺序。
- Redis 依赖和容器保留，职责收敛为缓存、限流和分布式协调。
- 已在 Temurin JDK 21.0.11 容器运行 `mvn -B clean test`：42 个测试通过，0 失败。
- 本地 `smartkb-postgres` 与 `smartkb-redis` 容器当前运行中，供下一阶段联调使用。

## 2026-07-21 企业 RAG Phase 1b

- 已建立 `ConversationRepository` 出站端口、`ConversationMessage` 领域记录和 JDBC/PostgreSQL 适配器。
- `PostgresChatMemory` 现在只负责 Spring AI 消息转换，通过领域端口访问会话事实，不再直接执行 SQL。
- 已增加端口和 JDBC 仓储单元测试；Temurin JDK 21.0.11 下 `mvn -B clean test` 为 43 个测试通过、0 失败。

## 2026-07-21 JDK 21 验证

- 已使用 `maven:3.9-eclipse-temurin-21` 容器执行全新 `mvn -B clean verify`。
- 验证环境为 Temurin JDK 21.0.11、Maven 3.9.16。
- 验证结果：43 个测试全部通过，项目成功编译并生成可执行 Jar。
- `docker compose config --quiet` 与 `git diff --check` 通过。
- 本轮未执行真实 PostgreSQL、Redis、Ollama、Reranker 和 ChatModel 联调，也未执行压力测试。

## 2026-07-21 敏感信息审计

- 当前分支和远程引用仅包含预期的可达提交，没有标签或额外 Git 引用指向旧历史。
- 可达历史未命中常见 API 密钥、访问令牌或私钥格式；`.env` 从未被 Git 跟踪，并已由 `.gitignore` 忽略。
- 旧版 `k8s/README.md` 中的 `sk-...` 命中均为占位示例，不是真实密钥。
- 本机 `.git` 对象库仍保留 184 个不可达旧提交；正常推送不会包含这些对象，但本地彻底清理需要过期 reflog 并执行 Git GC。
- 未执行不可逆的 reflog/Git 对象清理，也未确认历史密钥是否已在供应商后台作废。

## 2026-07-11 补充进度

- 已使用最新代码重启本地 SmartKB，并完成正常回答、低置信度拒答和答案 Judge 的真实模型验收。
- 已新增 `docs/ACCEPTANCE_TEST_CASES.md`，记录可复现的 6 组验收用例和本次结果。
- 已重新生成 10 幅桌面演示截图、新版 GIF 和 MP4，新增低置信度拒答与答案质量 Judge 画面。

- 已增加 Advanced RAG 低置信度拒答，返回 `confidence`、`refused`、`refusalReason`，拒答时跳过答案生成。
- 已增加 `POST /api/rag/eval/answer`，通过 LLM-as-Judge 输出 Faithfulness、Answer Relevance、Context Relevance 和综合分。
- 前端 Advanced RAG 消息已展示证据置信度与拒答原因。
- 已补充 Judge 解析、分数截断、输入校验和 Controller 契约测试。
- 已更新 README、TESTING、SPEC 与环境变量示例。
- 已验证：`mvn verify` 通过（43 tests），Python 编译、Docker Compose 配置和 `git diff --check` 通过。
- 未执行：未重启当前本地 SmartKB 进程进行真实模型接口手测；未 commit、未 push。

## 当前目标

将 SmartKB 整理为公开 GitHub 项目，聚焦可解释、可评测的 Java RAG 工程闭环。

## 当前阶段

企业级 RAG 重构 Phase 2 已完成，下一阶段为 Milvus/OpenSearch 双路召回与索引一致性。

## 已完成

- 移除 Agent 工作台代码、页面、测试和设计文档。
- README、SPEC、DEMO、TESTING 收敛为 RAG 主线。
- 保留文档入库、Hybrid Search、流式问答、引用定位、评测和监控。
- 已引入 Flyway V1 企业 RAG 核心表，并将 ChatMemory 从 Redis 切换为 PostgreSQL 持久化。
- 已在 JDK 21 环境完成全新构建，43 个自动化测试全部通过。

## 下一阶段（待新对话）

- Phase 3 设计与实施：Milvus/OpenSearch 双路召回、RRF 融合和索引一致性。

## 下一步

- 新开对话后先为 Phase 3 编写和评审 Milvus/OpenSearch 检索与一致性 SPEC，再拆分实施任务。

## 风险

- 本机不可达 Git 对象中可能仍保留旧敏感内容，不应复制或发布整个 `.git` 目录。
- reflog 过期和 Git GC 会永久删除旧对象，执行前需要用户明确确认。
