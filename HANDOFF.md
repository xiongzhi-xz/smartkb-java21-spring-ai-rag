# HANDOFF - SmartKB

## 当前目标

把 SmartKB 收口为可本地运行、可演示、可面试讲解的 Java 21 + Spring AI RAG 项目，并作为后续 AI 工程化转型主项目继续升级。

新的求职叙事方向：

- 第一阶段：企业智能知识库，展示 Spring AI、Advanced RAG、pgvector、Redis ChatMemory、Docker Compose、Prometheus/Grafana。
- 第二阶段：升级为面向 Java 存量项目的 Agent 工程平台，增加项目接管、任务状态机、长期记忆、上下文检索和 eval 能力。

## 当前阶段

Docker Compose 全链路启动已验证通过，RAG 收口阶段基本完成。当前进入 SmartKB v2 Agent 工程平台规划阶段。

## 已完成

- PostgreSQL + pgvector 向量存储。
- Ollama `nomic-embed-text` 本地 Embedding。
- Spring Boot `hybrid` 本地运行模式。
- 文档上传、解析、切片、向量入库。
- 普通 RAG、多轮对话、流式输出。
- Advanced RAG：查询改写、文档过滤、引用片段、阶段反馈、阶段耗时指标。
- Hybrid Search 基础版：向量召回 + 关键词召回融合。
- Redis ChatMemory：会话持久化、TTL、降级到 InMemory。
- DeepSeek 模型接入和异常友好提示。
- Micrometer/Prometheus 自定义指标。
- Docker Compose 全链路一键启动（含 Spring Boot 多阶段构建容器）。
- Grafana Dashboard 预配置（数据源 + RAG 监控面板）。
- Dockerfile 多阶段构建优化：容器内完成 Maven 编译，阿里云镜像加速，exec 形式 ENTRYPOINT 支持优雅停机。
- SmartKB v2 Agent 工程平台规格文档：`docs/AGENT_PLATFORM_SPEC.md`。
- SmartKB v2 首批 Agent eval 模板：`docs/agent-eval-report.md`，包含 TicketRush 10 个 eval case。

## 正在做

正在补齐 SmartKB v2 的项目接管、任务状态机、记忆分层、代码检索和 eval 规格。

## 下一步

1. 手动执行 `docs/agent-eval-report.md` 中的 E01：接管 TicketRush 项目。
2. 根据 E01 结果调整项目接管输出格式。
3. 执行 E02-E04，校准代码上下文检索策略。
4. 验证 Redis ChatMemory 完整清单（见 SPEC.md 的 6 项验证点）。
5. K3s 部署方案设计。

## 已修改文件

本轮改动：

- `docs/AGENT_PLATFORM_SPEC.md` — SmartKB v2 Agent 工程平台规格
- `docs/agent-eval-report.md` — SmartKB v2 首批 Agent eval 模板和 TicketRush 10 个 case
- `SPEC.md` — 增加 SmartKB v2 入口
- `HANDOFF.md` — 更新当前阶段和下一步

安全性检查：
- 本轮只改文档，无密钥、token、私有路径或账号信息。

## 已验证

- `mvn test` 通过，共 11 个测试，0 失败。
- `git diff --check` 仅 LF/CRLF 警告，无空白问题。
- Docker 多阶段构建成功：`docker compose up -d --build smartkb-app` 镜像构建 + 容器启动正常。
- 容器健康检查：`smartkb-app` 状态 healthy，`http://localhost:8082/` 返回 200。
- Health endpoint：`http://localhost:8082/actuator/health` 返回 `{"status":"UP"}`（db=PostgreSQL UP, redis=UP, disk=UP）。
- Prometheus + Grafana 容器正常运行。

## 未验证

- Redis ChatMemory 重启后会话恢复（SPEC.md 验证清单 6 项）。
- Prometheus/Grafana 指标采集和 Dashboard 展示。
- K3s 部署。

## 风险和注意事项

- 不要把真实 API key、token、cookie、私钥或 `.env` 内容写入仓库。
- 不要复述密钥值，检查配置时只说明字段是否存在。
- 不要删除数据库数据或已上传文档，除非用户明确要求。
- 不要擅自修改数据库 schema、迁移文件或生产配置。
- 当前前端是 Spring Boot 静态页：`src/main/resources/static/index.html`，不是 Vue/Vite 项目。
- SmartKB 首页：本地 IDEA 启动为 `http://localhost:8080`，Docker 模式为 `http://localhost:8082`。
- 如果数据库里有修复前上传的乱码文档，代码修复不会自动修复旧数据，需要删除后重新上传。
- `.mvn/settings.xml` 中 `spring-milestones` 仓库不走阿里云镜像（`mirrorOf=spring-release`），因为阿里云未同步 Spring AI milestone 包。如果后续 Spring AI 升级到正式版，可改为 `mirrorOf=spring-milestones` 以加速下载。

## 接管开场模板

新窗口或换模型时，先执行：

```powershell
Get-Content -Raw HANDOFF.md
Get-Content -Raw SPEC.md
git status --short
git log --oneline -5
```

然后先输出：

```text
当前目标：
当前阶段：
已完成：
未完成：
工作区是否有未提交改动：
我下一步只做：
```

## 2026-06-17 Work Log - Eval Run JDBC

Current goal:
- Continue SmartKB v2 Agent Platform phase F, focusing on structured Eval Run records and persistence.

Completed:
- Added switchable Eval Run store abstraction.
- Added default `InMemoryEvalCaseRunStore`.
- Added optional `JdbcEvalCaseRunStore`, enabled by `smartkb.agent.eval-run.persistence=jdbc`.
- Added in-memory store tests for save, read, newest-first ordering, and filters.
- Added `docs/EVAL_RUN_JDBC_VERIFICATION.md`.
- Ran local JDBC smoke test with Docker Compose PostgreSQL/Redis and temporary app container `smartkb-jdbc-smoke`.

Verified:
- `mvn -Dtest=InMemoryEvalCaseRunStoreTest,EvalCaseRunServiceTest,EvalCaseRunImportServiceTest,EvalReportServiceTest test`: 14 tests passed.
- `mvn test`: 74 tests passed.
- `git diff --check`: only LF/CRLF warnings.
- Docker health endpoint in JDBC mode: `UP`.
- Eval API create/list/import/report in JDBC mode.
- Restart persistence: `E-JDBC` remained after app restart.
- Re-import after restart: `importedAfterRestart=0`, `skippedAfterRestart=10`.
- PostgreSQL table count: `total_runs=11`, `jdbc_smoke_runs=1`.

Modified files in latest JDBC verification stage:
- `docs/EVAL_RUN_JDBC_VERIFICATION.md`
- `SPEC.md`
- `docs/AGENT_PLATFORM_SPEC.md`
- `HANDOFF.md`

Cleanup:
- Removed temporary app container `smartkb-jdbc-smoke`.
- Stopped PostgreSQL and Redis containers.
- Did not delete Docker volumes or database rows.

Next step:
- Add an Eval Run JDBC automated integration test plan first. Do not introduce Testcontainers or new dependencies without a small design note and explicit reason.

## 2026-06-17 Work Log - Eval Run JDBC IT

Completed:
- Added `integration-tests` Maven profile with Failsafe for `*IT.java`.
- Added Testcontainers test-scope dependencies.
- Added `JdbcEvalCaseRunStoreIT`.
- Guarded the integration test with `@Testcontainers(disabledWithoutDocker = true)`.
- Updated `docs/EVAL_RUN_JDBC_INTEGRATION_TEST_PLAN.md` from proposal to implementation notes.

Verified:
- `mvn test`: passed, 74 tests.
- `mvn -Pintegration-tests verify`: passed.
- On this Windows Docker Desktop environment, `JdbcEvalCaseRunStoreIT` was skipped because Testcontainers could not find a valid Java Docker client configuration through npipe.
- Live JDBC behavior is still covered by the Docker Compose smoke record in `docs/EVAL_RUN_JDBC_VERIFICATION.md`.

Next step:
- Phase F Eval Run persistence is closed. Next safe work is to choose the next SmartKB v2 Agent platform capability without expanding TicketRush scope.

## 2026-06-17 Work Log - K3s Plan

Completed:
- Added `docs/K3S_DEPLOYMENT_PLAN.md`.
- Added `k8s/k3s-demo.yaml`.
- Marked K3s deployment plan as complete in `SPEC.md`.
- Linked the plan from `k8s/README.md`.

Important note:
- `k8s/deployment.yaml` is still a draft and was not modified in this step.
- `k8s/k3s-demo.yaml` has corrected app environment variables and Secret references, but still needs real K3s/K3d verification before being treated as deploy-ready.
- YAML syntax check passed with `npx --yes js-yaml k8s/k3s-demo.yaml`.
- `kubectl apply --dry-run=client` could not run because no Kubernetes API server is reachable in this environment.

Next step:
- If continuing K3s work, verify `k8s/k3s-demo.yaml` on a disposable local K3s/K3d cluster.

## 2026-06-17 Work Log - RedisChatMemory Unit Coverage

Completed:
- Added `RedisChatMemoryTest`.
- Fixed JSON escape handling in `RedisChatMemory.extractJsonValue` so escaped values such as `\n` survive deserialization.
- Covered Redis list append, TTL refresh, max-message trim, latest-message read, and clear behavior with Mockito tests.

Verified:
- `mvn -Dtest=RedisChatMemoryTest test`: 5 tests passed.
- `mvn test`: 79 tests passed.

Still not verified:
- The SPEC Redis ChatMemory checklist remains unchecked because it requires a running SmartKB app plus real conversation/Advanced RAG calls.

## 2026-06-17 Work Log - Redis Live Smoke Attempt

Attempted:
- Start SmartKB Docker Compose app to verify Redis ChatMemory mode and backend clear endpoint without LLM calls.

Result:
- `docker compose up -d --build smartkb-app` timed out after 5 minutes.
- Residual Docker build/compose child processes were stopped.
- After that, Docker Desktop endpoints `npipe:////./pipe/dockerDesktopLinuxEngine` and `npipe:////./pipe/docker_engine` were unavailable.
- Did not restart Docker Desktop because TicketRush containers had been running and this could disrupt the user's other environment.

Still not verified:
- Redis ChatMemory live startup log.
- Redis key creation through a real conversation request.
- Backend DELETE `/api/chat/memory/{conversationId}` against a running app.

## 2026-06-17 Work Log - ChatMemory Clear Endpoint Test

Completed:
- Added `SmartKbControllerTest` WebMvc slice coverage for DELETE `/api/chat/memory/{conversationId}`.
- Covered successful `chatMemory.clear(conversationId)` delegation.
- Covered failed clear response when the ChatMemory layer throws.
- Marked the backend clear endpoint regression test in `SPEC.md`.

Verified:
- `mvn -Dtest=SmartKbControllerTest,RedisChatMemoryTest test`: 7 tests passed.
- `mvn test`: 81 tests passed.

Still not verified:
- The running-app Redis live smoke remains blocked by unavailable Docker Desktop endpoints.
- The SPEC Redis ChatMemory live checklist still needs a real SmartKB app plus Redis.

## 2026-06-17 Work Log - ConversationId Web Contract Test

Completed:
- Expanded `SmartKbControllerTest` to cover conversationId handling for POST `/api/chat/conversation`.
- Verified conversation chat reuses a provided `conversationId`.
- Verified conversation chat generates a non-blank `conversationId` when omitted.
- Verified POST `/api/chat/advanced` passes the provided `conversationId` and metadata filter to `AdvancedRagService`.

Verified:
- `mvn -Dtest=SmartKbControllerTest test`: 5 tests passed.

Still not verified:
- Full running-app Redis/LLM behavior is still blocked by unavailable Docker Desktop endpoints and external model dependencies.

## 2026-06-17 Work Log - Project Intake Text Extractor Tests

Completed:
- Added direct unit coverage for `ProjectIntakeTextExtractor`.
- Covered Markdown section extraction up to the next level-two heading.
- Covered paragraph, first meaningful line, bullet, numbered list, checked item, merge, limit, first item, and first non-blank helpers.
- Marked the parser coverage in `SPEC.md`.

Verified:
- `mvn -Dtest=ProjectIntakeTextExtractorTest test`: 4 tests passed.
- `mvn test`: 88 tests passed.

## 2026-06-17 Work Log - Project Intake Detector Tests

Completed:
- Added direct unit coverage for `ProjectIntakeDetector`.
- Covered stack detection for Java 21, Spring Boot, Maven, Docker Compose, Redis, RocketMQ, Prometheus, and Grafana.
- Covered build tool priority, test command suggestions, package manager evidence order, evidence type classification, and empty unknown stack behavior.
- Marked detector coverage in `SPEC.md`.

Verified:
- `mvn -Dtest=ProjectIntakeDetectorTest test`: 5 tests passed.
- `mvn test`: 93 tests passed.

## 2026-06-17 Work Log - Eval Report Aggregation Edge Test

Completed:
- Added Eval report coverage for trimmed project IDs and normalized failure reasons.
- Covered failure reason grouping by count and deterministic secondary ordering.
- Covered null score, max score, duration, human intervention, and tool-call metrics in aggregate reports.
- Marked the Eval report aggregation edge coverage in `SPEC.md`.

Verified:
- `mvn -Dtest=EvalReportServiceTest test`: 4 tests passed.
- `mvn test`: 94 tests passed.

## 2026-06-17 Work Log - Eval Run Store Idempotent Save Test

Completed:
- Added in-memory Eval Run store coverage for repeated saves with the same run id.
- Verified duplicate saves replace the stored run and do not duplicate the run order list.
- Marked the store idempotency coverage in `SPEC.md`.

Verified:
- `mvn -Dtest=InMemoryEvalCaseRunStoreTest test`: 4 tests passed.
- `mvn test`: 95 tests passed.

## 2026-06-17 Work Log - MemoryRecord Normalization Test

Completed:
- Added MemoryRecord service coverage for trimming projectId, sourceType, sourcePath, content, and tags.
- Verified list filters also normalize projectId and sourceType.
- Marked MemoryRecord normalization coverage in `SPEC.md`.

Verified:
- `mvn -Dtest=MemoryRecordServiceTest test`: 6 tests passed.
- `mvn test`: 96 tests passed.

## 2026-06-17 Work Log - AgentTask Normalization Test

Completed:
- Added AgentTask service coverage for trimming projectId, title, goal, transition note, and plan.
- Verified blank riskLevel defaults to `medium`.
- Verified blank transition values do not overwrite an existing plan.
- Marked AgentTask normalization coverage in `SPEC.md`.

Verified:
- `mvn -Dtest=AgentTaskServiceTest test`: 6 tests passed.
- `mvn test`: 97 tests passed.

## 2026-06-17 Work Log - RedisChatMemory Integration Coverage

Completed:
- Checked Redis live smoke prerequisites without restarting Docker Desktop.
- Confirmed Docker Desktop service was stopped and Docker API was unavailable.
- Confirmed ports `5432` and `6379` were not listening; only Ollama was listening on `11434`.
- Confirmed no local `redis-server`, `redis-cli`, `psql`, or PostgreSQL service was available.
- Added `RedisChatMemoryIT` under the existing `integration-tests` profile.
- Added `docs/REDIS_CHAT_MEMORY_VERIFICATION.md`.
- Updated `SPEC.md` with the RedisChatMemory integration-test coverage and current test count.

Verified:
- `mvn -Dtest=RedisChatMemoryTest test`: 5 tests passed.
- `mvn test`: 97 tests passed.
- `mvn -Pintegration-tests verify`: build passed; `JdbcEvalCaseRunStoreIT` and `RedisChatMemoryIT` were skipped because Testcontainers could not find a valid Docker environment.

Still not verified:
- SPEC Redis ChatMemory live checklist remains unchecked because it needs a running SmartKB app, real Redis, browser conversation state, and LLM calls.
- Docker Desktop was not restarted to avoid disrupting other local project environments.

Next step:
- After Docker Desktop is available, follow `docs/REDIS_CHAT_MEMORY_VERIFICATION.md` and complete the six Redis live checklist items in `SPEC.md`.

## 2026-06-17 Work Log - RedisChatMemory Live Partial Verification

Completed:
- Continued after the user started Docker Desktop.
- Confirmed Docker CLI was available and Docker server was `29.5.3`.
- Confirmed `smartkb-app` was already healthy at `http://localhost:8082`.
- Confirmed `smartkb-postgres` and `smartkb-redis` were healthy.
- Verified `smartkb-app` startup logs include `初始化 RedisChatMemory, TTL=24h`.
- Verified `smartkb-app` startup logs include `初始化 ChatMemory (Redis 模式, TTL=24h)`.
- Verified actuator health returned `UP` with PostgreSQL and Redis `UP`.
- Sent POST `/api/chat/conversation` with a dedicated `conversationId`.
- Verified Redis key creation under `smartkb:chat:{conversationId}` despite the Chat model returning 401.
- Verified the Redis key type was `list`, list length was `1`, and TTL was positive near 24 hours.
- Verified DELETE `/api/chat/memory/{conversationId}` returned success and removed the Redis key.
- Updated `SPEC.md` to mark 3 Redis live checklist items complete.
- Updated `docs/REDIS_CHAT_MEMORY_VERIFICATION.md` with the live verification record.

Verified:
- `mvn -Pintegration-tests verify`: build passed; 97 unit tests passed, 2 integration tests skipped because Testcontainers still could not find a valid Java Docker client through Windows npipe.

Still not verified:
- Browser refresh follow-up memory.
- App restart follow-up memory.
- Advanced RAG history-aware query rewrite.
- These require a valid Chat token. The running request failed with `401 无效的令牌`; host env vars `TRANSIT_API_KEY`, `OPENAI_API_KEY`, `TRANSIT_BASE_URL`, and `AI_MODEL` were absent.

Next step:
- Provide a valid Chat token to the running SmartKB app, restart only `smartkb-app`, then complete the remaining three Redis live checklist items.

## 2026-06-17 Work Log - RedisChatMemory Live Completed

Completed:
- Created local ignored `.env` from `.env.example`.
- Normalized `.env` from `export KEY=value` to Docker Compose-compatible `KEY=value` without printing secret values.
- Removed non-`KEY=value` example command lines from `.env`.
- Updated `docker-compose.yml` so container-mode `OPENAI_API_KEY` falls back to local `TRANSIT_API_KEY`.
- Recreated `smartkb-app` with the new `.env`; confirmed required environment fields were present inside the container without printing values.
- Found a live behavior bug: Redis history was read after app restart, but conversation mode still answered as if no memory existed because the default `QuestionAnswerAdvisor` no-document constraint could outweigh ChatMemory history.
- Added a dedicated `conversationChatClient` with only `MessageChatMemoryAdvisor`.
- Updated `RagService.queryWithContext` and stream mode to use the dedicated conversation client.
- Verified Redis ChatMemory live checklist end to end with `conversationId=redis-memory-fixed-20260617122343`.
- Marked all six Redis ChatMemory checklist items complete in `SPEC.md`.
- Updated `docs/REDIS_CHAT_MEMORY_VERIFICATION.md` with the completed live record.

Verified:
- `mvn test`: 97 tests passed.
- `mvn package -DskipTests`: passed.
- `smartkb-app` logs include `初始化 Conversation ChatClient with ChatMemory Advisor`.
- Same `conversationId` follow-up returned phrase `smartkb-cypress-122343`.
- Restarted only `smartkb-app`; post-restart follow-up returned the same phrase.
- Advanced RAG loaded 6 ChatMemory messages and query rewrite included `smartkb-cypress-122343`.
- DELETE `/api/chat/memory/{conversationId}` removed the Redis key; `exists` returned `0`.

Operational note:
- Full Docker image rebuild with `docker compose up -d --no-deps --build --force-recreate smartkb-app` timed out after 5 minutes in this environment.
- The stuck local Docker build processes were stopped.
- Live verification used `mvn package -DskipTests`, `docker cp target/smartkb-java21-spring-ai-rag-1.0.0-SNAPSHOT.jar smartkb-app:/app/app.jar`, then `docker restart smartkb-app`.
- For a durable container image, retry full Docker build later or optimize the Dockerfile build cache.

Next step:
- Redis ChatMemory live verification is closed. A good next task is either optimizing Docker rebuild speed or continuing SmartKB v2 Agent platform polish.

## 2026-06-17 Work Log - Docker Build Reproducibility

Completed:
- Added `.dockerignore` to keep `.env`, `.git`, `target`, IDE files, docs, k8s manifests, monitoring assets, and local compose files out of the Docker build context.
- Updated `Dockerfile` to use BuildKit syntax `docker/dockerfile:1.7`.
- Moved Maven settings to `/tmp/maven-settings.xml`.
- Added BuildKit cache mounts for `/root/.m2/repository` in both `dependency:go-offline` and `mvn clean package -DskipTests`.
- Verified full Docker build no longer needs the manual `docker cp` workaround.
- Recreated `smartkb-app` from the rebuilt image without touching PostgreSQL, Redis, or TicketRush containers.

Verified:
- `docker compose build smartkb-app --progress=plain`: passed.
- First cold/cache-fill build transferred only `11.08kB` build context and completed successfully. It still took about 6.5 minutes because Maven dependencies had to populate the BuildKit cache.
- `docker compose --progress=plain build smartkb-app`: passed in about 1.5 seconds with Maven build steps `CACHED`.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed in about 9 seconds with cached layers.
- `smartkb-app` returned healthy.
- `http://localhost:8082/actuator/health` returned `UP` with PostgreSQL and Redis `UP`.

Notes:
- `.env` remains ignored and was not committed.
- The optimized path depends on BuildKit, which Docker Desktop already used successfully in this environment.

Next step:
- Docker rebuild risk is closed. Continue with SmartKB v2 Agent platform polish or optionally improve Testcontainers Windows npipe compatibility.

## 2026-06-17 Work Log - Project Intake Report Quality

Current goal:
- Make Project Intake output closer to a real takeover report, not only extracted fields.

Completed:
- Added `takeoverBrief`, `stackEvidence`, `runnableCommands`, and `verificationGaps` to `ProjectIntakeResponse.IntakeSummary`.
- Updated `ProjectIntakeService` to merge HANDOFF/SPEC progress, produce a takeover brief, expose stack evidence, suggest runnable commands, and list verification gaps.
- Updated the static Project Intake panel to display the new summary fields.
- Expanded service and Web layer assertions for the new response fields.
- Marked the enhancement complete in `SPEC.md`.

Modified files:
- `src/main/java/com/smartkb/agent/domain/ProjectIntakeResponse.java`
- `src/main/java/com/smartkb/agent/application/ProjectIntakeService.java`
- `src/main/resources/static/index.html`
- `src/test/java/com/smartkb/agent/application/ProjectIntakeServiceTest.java`
- `src/test/java/com/smartkb/agent/controller/ProjectIntakeControllerTest.java`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- `mvn "-Dtest=ProjectIntakeServiceTest,ProjectIntakeControllerTest" test`: 5 tests passed.
- `mvn test`: 97 tests passed.
- `git diff --check`: only LF/CRLF warnings, no whitespace errors.

Not verified:
- Browser UI smoke against a rebuilt/rerun app. The static page code is updated and covered by backend contract tests, but the running Docker container may still need rebuild/restart to serve the new HTML.

Next step:
- Continue SmartKB v2 Agent platform polish. A good next slice is to smoke-test the Project Intake panel in the running app after rebuilding `smartkb-app`, then improve the takeover report wording if the UI output feels too dense.
