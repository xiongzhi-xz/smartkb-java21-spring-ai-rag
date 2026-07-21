# SmartKB Handoff

## 2026-07-21 企业 RAG Phase 1a

- 已引入 Flyway，并在本地 PostgreSQL 16 上实际执行 `V1__enterprise_rag_core.sql`。
- 已创建知识库、文档、Chunk、入库任务、会话、消息和检索 trace 共 7 张核心表。
- 原 `vector_store` 表已确认保留，未被迁移修改或删除。
- Spring AI ChatMemory 已从 Redis List 切换为 PostgreSQL 持久化实现，使用原子会话序号保证并发写入顺序。
- Redis 依赖和容器保留，职责收敛为缓存、限流和分布式协调。
- 已在 Temurin JDK 21.0.11 容器运行 `mvn -B clean test`：42 个测试通过，0 失败。
- 本地 `smartkb-postgres` 与 `smartkb-redis` 容器当前运行中，供下一阶段联调使用。

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

企业级 RAG 重构 Phase 1a 已完成，准备收敛领域端口与持久化适配器。

## 已完成

- 移除 Agent 工作台代码、页面、测试和设计文档。
- README、SPEC、DEMO、TESTING 收敛为 RAG 主线。
- 保留文档入库、Hybrid Search、流式问答、引用定位、评测和监控。
- 已引入 Flyway V1 企业 RAG 核心表，并将 ChatMemory 从 Redis 切换为 PostgreSQL 持久化。
- 已在 JDK 21 环境完成全新构建，43 个自动化测试全部通过。

## 正在做

- 单租户企业 RAG 重构：Milvus + OpenSearch + PostgreSQL + MinIO + RabbitMQ + Redis。
- 收敛领域端口、Repository 适配器与 Redis 缓存边界。

## 下一步

- 实施 Phase 1b：建立领域端口、Repository 适配器与 Redis 缓存适配层。
- 实施 Phase 2 前确认 MinIO、RabbitMQ 的本地部署参数与重试策略。

## 风险

- 本机不可达 Git 对象中可能仍保留旧敏感内容，不应复制或发布整个 `.git` 目录。
- reflog 过期和 Git GC 会永久删除旧对象，执行前需要用户明确确认。
