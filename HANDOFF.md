# HANDOFF - SmartKB

## Latest Snapshot - 2026-06-18

Current goal:
- Keep SmartKB stable as a local demoable Java 21 + Spring AI RAG project and close the final Project Intake frontend takeover-report polish.

Current stage:
- Docker Compose, RAG demo flow, Agent workbench, Eval Run persistence, README presentation, static workbench regression tests, local K3d demo verification, and the final Project Intake frontend takeover-report enhancement are complete.
- The verified Kubernetes demo entry point is `k8s/k3s-demo.yaml`.
- The historical Kubernetes draft is `k8s/deployment-draft.yaml` and must not be applied directly.

Recently completed:
- Added a Project Intake frontend takeover report that mirrors the AGENTS/HANDOFF takeover shape: current goal, current stage, completed, unfinished, workspace status, and next step only.
- Extended `scripts/smoke/workbench-summary-smoke.mjs` to assert the takeover report renders 6 rows and still has no 390px horizontal overflow.
- Updated `SPEC.md` and `TESTING.md` for this final frontend handoff-report slice.

Workspace status:
- Check the latest pushed commit with `git log --oneline -5`.
- Check the current working tree with `git status --short --branch`.

Verified latest:
- `node --check .\scripts\smoke\workbench-summary-smoke.mjs`: passed.
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 5 tests passed.
- `node .\scripts\smoke\workbench-summary-smoke.mjs`: passed locally; takeover report rows `6`, Project Intake metrics `3,2,1,0`, AgentTask metrics `4,2,1,1`, Code Context metrics `2,1,1,yes`, Eval metrics `3,1,1,1`, no 390px overflow.
- `mvn test`: 106 tests passed.
- `git diff --check`: passed with LF/CRLF warnings only.

Next step only:
- Stop expanding features. Only run final verification, commit this slice, and use this HANDOFF if another window needs to inspect the closed state.

Note:
- Older sections below are historical work logs and may describe earlier "current" tasks before the K3d verification was completed.

## 2026-06-18 Work Log - Project Intake Frontend Takeover Report

Current goal:
- Finish the final Project Intake frontend takeover-report enhancement and close the SmartKB v2 polish round without adding new feature scope.

Completed:
- Added `renderProjectIntakeTakeoverReport(...)` in `src/main/resources/static/index.html`.
- The Project Intake result now shows a first-screen takeover report with six AGENTS/HANDOFF-compatible rows: current goal, current stage, completed, unfinished, workspace status, and next step only.
- Added compact list rendering for completed/unfinished rows so long reports stay scannable on mobile.
- Extended static HTML guards and browser summary smoke coverage for the takeover report.
- Updated `SPEC.md` and `TESTING.md`.

Modified files:
- `src/main/resources/static/index.html`
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java`
- `scripts/smoke/workbench-summary-smoke.mjs`
- `SPEC.md`
- `TESTING.md`
- `HANDOFF.md`

Verified:
- `node --check .\scripts\smoke\workbench-summary-smoke.mjs`: passed.
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 5 tests passed.
- `node .\scripts\smoke\workbench-summary-smoke.mjs`: passed locally with 6 takeover-report rows and no 390px horizontal overflow.
- `mvn test`: 106 tests passed.
- `git diff --check`: passed with LF/CRLF warnings only.

Next step:
- Run final project verification and commit. Do not start another feature unless the user explicitly opens a new scope.

## 当前目标

把 SmartKB 收口为可本地运行、可演示、可面试讲解的 Java 21 + Spring AI RAG 项目，并作为后续 AI 工程化转型主项目继续升级。

新的求职叙事方向：

- 第一阶段：企业智能知识库，展示 Spring AI、Advanced RAG、pgvector、Redis ChatMemory、Docker Compose、Prometheus/Grafana。
- 第二阶段：升级为面向 Java 存量项目的 Agent 工程平台，增加项目接管、任务状态机、长期记忆、上下文检索和 eval 能力。

## 当前阶段

Docker Compose 全链路启动、Redis ChatMemory live 验证、Docker 构建收口、Agent 工作台、Project Intake / Code Context 摘要指标、移动端响应式布局、移动端表单 smoke、MemoryRecord 前端工作区、Memory 浏览器点击 smoke、Eval Run 持久化、README 展示页和静态工作台结构回归测试均已完成。当前处于 SmartKB v2 Agent 工程平台打磨阶段。

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
- 静态工作台 HTML 结构回归测试：覆盖工作区导航、AgentTask/Eval 子 Tab、静态 ID 唯一性和核心函数存在性。
- Project Intake / Code Context 摘要指标：结果顶部展示技术栈、可运行命令、验证缺口、警告、结果数、跳过数和 Git 状态。
- AgentTask 摘要指标：任务列表顶部展示总数、进行中、已记录和待介入。
- MemoryRecord 摘要指标：列表顶部展示总数、高权威、来源类型和标签数。
- 移动端工作台布局：小屏下侧栏变为顶部工作区入口，主工作区保持完整宽度，无横向溢出。
- 移动端表单 smoke：Project Intake、Code Context、AgentTask 和 Eval 在 390px 视口通过。
- 工作区隐藏兜底：本页自有 CSS 提供 `.hidden { display: none !important; }`，避免 Tailwind CDN 慢或失败时面板串出。
- MemoryRecord 前端工作区：支持导入高权威记忆、手工新增记忆、列表查看和冲突检查。
- MemoryRecord 列表摘要指标：总数、高权威、来源类型、标签数，并已纳入 mobile edge smoke。
- MemoryRecord 浏览器点击 smoke：覆盖 6 个工作区切换、页面不跳转、无横向溢出、手工新增记忆后列表可见。
- Eval 运行记录摘要指标：总数、通过、部分、失败，并已纳入 summary smoke。

## 正在做

正在做 SmartKB v2 Agent 工程平台的体验、测试和展示收口。

## 下一步

1. 继续 SmartKB v2 Agent 平台体验打磨，例如更密集的 Project Intake / Memory / Code Context 信息展示。
2. 生产级 K3s/Kubernetes 部署仍需单独设计，包括镜像仓库、TLS、监控、托管 Secret 和数据库 HA。

## 已修改文件

本轮改动：

- `src/main/resources/static/index.html` — 增加 Project Intake / Code Context 摘要指标
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java` — 增加摘要指标 helper 静态守卫
- `README.md` — 更新验证状态
- `SPEC.md` — 标记 Project Intake / Code Context 摘要指标
- `HANDOFF.md` — 记录本轮验证结果和 Docker runtime 风险

安全性检查：
- 本轮不涉及密钥、token、私有路径或账号信息。

## 已验证

- `mvn -Dtest=StaticWorkbenchHtmlTest test`：5 tests passed。
- Inline JavaScript syntax check via Node：passed。
- `mvn test`：103 tests passed，0 failures，0 errors。
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`：passed。
- `smartkb-app` health：healthy，`/actuator/health` 返回 `UP`。
- 首页 HTML 包含 `workspaceNavMemory`。
- Headless Chrome CDP smoke passed：覆盖 6 个工作区（智能问答、项目接管、任务状态、记忆层、代码上下文、Eval 评测）切换，`window.scrollY=0`，无横向溢出。
- Memory 工作区手工新增记忆后，浏览器列表中可见新增内容。
- Headless Chrome mobile CDP smoke passed：390x844 视口覆盖 6 个工作区切换，侧栏/主区/面板宽度均为 390px，无横向溢出。
- 移动截图已人工检查：`target/mobile-workbench-chat.png`、`target/mobile-workbench-memory.png`。
- Headless Chrome mobile form CDP smoke passed：Project Intake 提交成功，Code Context 查询 `StaticWorkbenchHtmlTest` 成功，均无横向溢出。
- 移动表单截图已人工检查：`target/mobile-form-project-intake.png`、`target/mobile-form-code-context.png`。
- Headless Chrome mobile Agent/Eval CDP smoke passed：AgentTask 创建并流转到 PLAN，Eval 新增记录、运行列表、聚合报告均通过，均无横向溢出。
- Agent/Eval 移动截图已人工检查：`target/mobile-agent-task.png`、`target/mobile-eval-list.png`、`target/mobile-eval-report.png`。
- Mobile edge browser smoke passed：390px 视口覆盖长文本、必填错误提示、窄屏导航/按钮宽度，本地静态页和 Docker 运行态首页均通过。
- Local mocked browser summary smoke passed：Project Intake / Code Context 摘要指标均渲染成功，无横向溢出。
- Docker runtime summary smoke passed：`http://localhost:8082` 首页包含 `renderCompactMetric`，`node .\scripts\smoke\workbench-summary-smoke.mjs "http://localhost:8082/?v=summary-runtime-smoke"` 通过。
- Docker runtime summary smoke passed：`http://localhost:8082` 首页包含 `renderEvalRunSummary`，`node .\scripts\smoke\workbench-summary-smoke.mjs "http://localhost:8082/?v=eval-summary-runtime"` 通过，Eval 指标为 `3,1,1,1`。
- Docker runtime summary smoke passed：`http://localhost:8082` 首页包含 `renderAgentTaskSummary`，`node .\scripts\smoke\workbench-summary-smoke.mjs "http://localhost:8082/?v=agent-summary-runtime"` 通过，AgentTask 指标为 `4,2,1,1`。
- K3s demo manifest offline guard passed：`mvn -Dtest=K3sDemoManifestTest test` 覆盖 env、Secret、探针、Service、Ingress 和 PostgreSQL `PGDATA`。
- K3s/K3d runtime verification passed：一次性 `smartkb-demo` K3d 集群中 PostgreSQL、Redis、SmartKB 均 `Running`，PVC 均 `Bound`，port-forward 后 `/actuator/health` 返回 `UP`，`db`/`redis`/`diskSpace` 均 `UP`，`/api/agent/eval/report` 返回成功。
- `mvn -Dtest=StaticWorkbenchHtmlTest test`：5 tests passed。
- `mvn test`：103 tests passed，0 failures，0 errors。

## 未验证

- 生产级 K3s/Kubernetes 部署尚未设计；当前仅验证本地一次性 K3d demo。


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
- Runtime smoke after rebuild:
  - `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
  - `smartkb-app`: healthy at `http://localhost:8082`.
  - `http://localhost:8082/actuator/health`: `UP`, with PostgreSQL and Redis `UP`.
  - Home page HTML contains `接管简报`, `技术栈证据`, `可运行命令`, and `验证缺口`.
  - POST `/api/agent/projects/intake` against container path `/app` returned `success=true` and a non-empty `takeoverBrief`.
- Docker host-workspace mount smoke:
  - Added a read-only Compose mount from `${SMARTKB_PROJECTS_ROOT:-..}` to `${SMARTKB_PROJECTS_MOUNT:-/workspace/projects}`.
  - Recreated only `smartkb-app`; container stayed healthy.
  - Verified `/workspace/projects/smartkb-java21-spring-ai-rag` exists inside the container.
  - POST `/api/agent/projects/intake` with `rootPath=/workspace/projects/smartkb-java21-spring-ai-rag` returned `success=true`.
  - Detected stack included Java 21, Spring Boot, Maven, Docker Compose, Redis, Prometheus, and Grafana.
  - Runnable commands included `mvn test`, `mvn clean verify`, `docker compose up -d`, and `docker compose ps`.

Not verified:
- Browser click-through after entering the container path manually. Use `/workspace/projects/<project-dir>` in Docker mode, not a Windows `E:\...` path.

Next step:
- Continue SmartKB v2 Agent platform polish. A good next slice is to improve the takeover report wording if the UI output feels too dense.

## 2026-06-17 Work Log - Frontend Workspace Layout

Current goal:
- Make the SmartKB frontend easier to understand after the RAG and Agent features accumulated on one page.

Completed:
- Reworked the static homepage from a crowded top toolbar into a left-side workspace navigation.
- Added five clear workspaces: 智能问答, 项目接管, 任务状态, 代码上下文, Eval 评测.
- Changed workspace switching so only the selected functional panel is visible; RAG mode controls are only shown in the chat workspace.
- Kept the existing backend APIs and JavaScript function names compatible.
- Updated Project Intake and Code Context default paths to Docker container paths: `/workspace/projects/smartkb-java21-spring-ai-rag`.
- Localized the most visible AgentTask, Code Context, and Eval form labels/messages to Chinese.
- Added a small responsive guard so the sidebar does not force horizontal overflow on narrow screens.

Modified files:
- `src/main/resources/static/index.html`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- Inline JavaScript syntax check via Node: passed.
- `mvn test`: 97 tests passed.
- `git diff --check`: only LF/CRLF warnings, no whitespace errors.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
- `smartkb-app`: healthy at `http://localhost:8082`.
- `http://localhost:8082/actuator/health`: `UP`, with PostgreSQL and Redis `UP`.
- Home page HTML contains the new workspace navigation and Docker default project path.
- POST `/api/agent/projects/intake` with `rootPath=/workspace/projects/smartkb-java21-spring-ai-rag` returned `success=true`, a non-empty takeover brief, and 8 detected stack items.

Not verified:
- Browser screenshot smoke. `npx playwright screenshot` could not run because the Playwright Chromium binary was missing, and `npx playwright install chromium` timed out after 3 minutes. The leftover Playwright install node processes were stopped.

Next step:
- Open `http://localhost:8082` manually and click through the five left-side workspaces. If the page still feels dense, split AgentTask/Eval into smaller sub-tabs next.

## 2026-06-17 Work Log - Frontend Menu Jump Fix

Completed:
- Fixed workspace menu switching causing the browser window to jump downward.
- Removed automatic focus calls from workspace menu switching.
- Locked the page-level scroll with `overflow-hidden` and kept scrolling inside the selected workspace panel.
- Kept validation-time focus behavior for missing required fields.

Verified:
- Inline JavaScript syntax check via Node: passed.
- `git diff --check`: only LF/CRLF warnings, no whitespace errors.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
- `smartkb-app`: healthy at `http://localhost:8082`.
- Home page HTML includes page-level `overflow-hidden` and the workspace navigation.

Next step:
- Manually click through the left-side workspaces in the browser and confirm there is no viewport jump. If any panel still feels too tall, split that panel into smaller sub-tabs.

## 2026-06-17 Work Log - Frontend Sidebar Restore Fix

Completed:
- Fixed the homepage refresh layout bug where the left menu appeared missing and document controls moved into the main area.
- Root cause: the workspace navigation opened with `<nav>` but was closed with `</div>`, so the browser repaired the DOM differently after refresh.
- Corrected the closing tag to `</nav>`.
- Kept the desktop layout stable with a fixed left sidebar and right workspace area.

Verified:
- Inline JavaScript syntax check via Node: passed.
- `git diff --check`: only LF/CRLF warnings, no whitespace errors.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
- `smartkb-app`: healthy at `http://localhost:8082`.
- Headless Chrome screenshot confirmed the sidebar menu, upload/document list, chat header, chat body, and composer render in the correct areas after a fresh load.

Next step:
- Refresh `http://localhost:8082/?v=navtagfix` or hard-refresh `http://localhost:8082` in the browser to bypass any stale page cache.

## 2026-06-17 Work Log - Frontend Workspace Smoke Verification

Current goal:
- Continue SmartKB takeover and verify the latest frontend sidebar/layout fix in the running Docker app.

Completed:
- Followed the takeover flow by reading `SPEC.md`, `HANDOFF.md`, `README.md`, `AGENTS.md`, `CLAUDE.md`, git status, and recent git log.
- Confirmed the working tree was clean before doing verification.
- Confirmed `smartkb-app`, PostgreSQL, Redis, Prometheus, and Grafana containers were running; `smartkb-app` was healthy.
- Confirmed `http://localhost:8082/actuator/health` returned `UP` with PostgreSQL and Redis `UP`.
- Confirmed the served homepage contains the corrected `<nav>...</nav>` workspace navigation and page-level `overflow-hidden`.
- Captured a fresh headless Chrome screenshot for `http://localhost:8082/?v=navtagfix`; sidebar, upload/document list, chat header, chat body, and composer rendered in the expected areas.
- Ran a headless Chrome DevTools smoke that switched through all five workspaces: 智能问答, 项目接管, 任务状态, 代码上下文, Eval 评测.

Verified:
- Workspace switch smoke passed for all five workspaces:
  - Active menu matched the selected workspace.
  - Only the expected panel was visible.
  - Chat composer and RAG controls were visible only in 智能问答.
  - `window.scrollY` stayed `0`, so menu switching no longer jumps the viewport.
  - No horizontal page overflow was detected at 1440px desktop width.

Modified files:
- `HANDOFF.md` only.

Not verified:
- Mobile viewport screenshot smoke was not run in this continuation step.
- No Maven test run was needed because no Java/static source code was changed.

Next step:
- Frontend sidebar refresh risk is closed. A good next slice is to split the dense AgentTask/Eval panels into smaller sub-tabs or continue SmartKB v2 Agent platform polish.

## 2026-06-17 Work Log - AgentTask Eval Sub Tabs

Current goal:
- Reduce the density of the SmartKB v2 Agent workbench without changing backend APIs.

Completed:
- Added reusable inner workspace tab styling and `openWorkspaceSubTab(scope, targetId)`.
- Split AgentTask into three inner tabs: 当前任务, 新建任务, 任务列表.
- Kept AgentTask creation fields, transition fields, current task detail, and task list on the same API/function contracts.
- Moved AgentTask creation validation into the 新建任务 tab and switched to 当前任务 after successful creation or task selection.
- Split Eval into three inner tabs: 新增记录, 运行列表, 聚合报告.
- Moved the Eval project filter and refresh/import actions into a shared toolbar so records and reports use the same project ID.
- Marked the frontend sub-tab polish complete in `SPEC.md`.

Modified files:
- `src/main/resources/static/index.html`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- Inline JavaScript parse check via Node: passed.
- Static DOM id uniqueness check via Node: 76 unique IDs, no duplicates.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed; image rebuilt and `smartkb-app` restarted.
- `smartkb-app`: healthy at `http://localhost:8082`.
- `curl http://localhost:8082/actuator/health`: `UP`, with PostgreSQL and Redis `UP`.
- Runtime headless Chrome smoke against `http://localhost:8082/?v=subtabs`: passed.
- Smoke covered AgentTask sub-tabs, Eval sub-tabs, AgentTask create validation staying inside 新建任务, and no desktop horizontal overflow at 1440px.
- `mvn test`: passed, 97 tests.
- `git diff --check`: only LF/CRLF warnings, no whitespace errors.

Not verified:
- Mobile viewport screenshot smoke was not run in this step.

Next step:
- Continue SmartKB v2 Agent platform polish. A focused next slice is to add lightweight screenshots or automated smoke coverage for the static workbench, or improve the Project Intake/Memory display density next.

## 2026-06-17 Work Log - GitHub Safety Cleanup

Current goal:
- Make the pushed GitHub version safer as a private backup and easier to turn into a public showcase later.

Completed:
- Confirmed the working tree was clean before the safety pass.
- Confirmed `.env` is ignored and not tracked; `.env.example` is tracked as the public template.
- Replaced private Chat API gateway examples with a generic OpenAI-compatible DeepSeek example.
- Replaced the old token-shaped API key placeholder with `your-api-key-here`.
- Changed the Docker Project Intake default mount from a machine-specific absolute path to `..`.
- Updated README, STARTUP, AGENTS, OLLAMA setup docs, application configs, Compose config, SPEC, and historical HANDOFF note to match the generic public-safe examples.
- Checked unauthenticated GitHub API visibility; the repository returned 404, so it is not visible as a public repository from that view.

Modified files:
- `.env.example`
- `AGENTS.md`
- `README.md`
- `STARTUP.md`
- `docker-compose.yml`
- `docs/OLLAMA_SETUP.md`
- `src/main/resources/application.yml`
- `src/main/resources/application-hybrid.yml`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- Redacted scan for the previous private gateway domains and local default project path: no remaining matches in the checked public-facing files.
- `git diff --check`: only LF/CRLF warnings, no whitespace errors.
- `docker compose config --quiet`: passed.
- `mvn test`: passed, 97 tests.

Next step:
- Commit and push the safety cleanup, then continue README/showcase polishing when ready.

## 2026-06-17 Work Log - README Showcase Polish

Current goal:
- Turn SmartKB `README.md` into a GitHub/project-showcase entry page that supports interviews and quick project review.

Completed:
- Rewrote the README around two clear stages:
  - SmartKB v1: Java 21 + Spring AI Advanced RAG knowledge base.
  - SmartKB v2: Java project Agent engineering platform.
- Added a Mermaid architecture diagram covering RAG, Redis ChatMemory, Agent platform, and observability flow.
- Added feature checklist, technology table, Docker Compose startup, hybrid local startup, demo path, API overview, verification status, project structure, interview talking points, documentation navigation, and safety notes.
- Kept public-safe OpenAI-compatible example configuration and avoided private gateway references.
- Marked README showcase polish complete in `SPEC.md`.

Modified files:
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- README local link scan via Node: passed (`README links ok: 15`).
- Public-safety scan for old private gateway/local path placeholders: no matches.
- `git diff --check`: only LF/CRLF warnings, no whitespace errors.
- `mvn test` was not rerun because this step only changed documentation.

Next step:
- Run documentation validation, commit, push, then continue TicketRush README showcase polish.

## 2026-06-18 Work Log - Static Workbench HTML Regression Test

Current goal:
- Add lightweight automated coverage for the static SmartKB workbench structure.

Completed:
- Added `StaticWorkbenchHtmlTest`.
- Covered the five main workspace nav buttons and panels.
- Covered AgentTask inner tabs: 当前任务, 新建任务, 任务列表.
- Covered Eval inner tabs: 新增记录, 运行列表, 聚合报告.
- Covered core frontend functions such as `openWorkspacePanel`, `openWorkspaceSubTab`, `runProjectIntake`, `createAgentTask`, `runCodeContext`, and Eval actions.
- Covered static HTML id uniqueness to catch single-file workbench regressions.
- Updated README, SPEC, and HANDOFF test counts/status.

Modified files:
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java`
- `SPEC.md`
- `README.md`
- `HANDOFF.md`

Verified:
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 4 tests passed.
- `mvn test`: 101 tests passed, 0 failures, 0 errors.

Not verified:
- Docker Compose runtime smoke was not rerun because this task only adds tests and documentation.
- Mobile screenshot smoke remains a future polish item.

Next step:
- Continue SmartKB v2 Agent platform polish, likely Project Intake / Memory / Code Context display density or mobile/screenshot smoke.

## 2026-06-18 Work Log - Memory Workspace

Current goal:
- Make MemoryRecord visible and usable from the SmartKB v2 workbench.

Completed:
- Added a new left-nav workspace: `记忆层`.
- Added high-authority memory import from a project path.
- Added manual memory creation with authority level, source type/path, tags, and content.
- Added memory list rendering with authority badges and tags.
- Added conflict checking against higher-authority memories.
- Wired the panel to existing MemoryRecord APIs without changing backend contracts.
- Updated README and SPEC.
- Extended `StaticWorkbenchHtmlTest` to cover the memory workspace and core memory functions.

Modified files:
- `src/main/resources/static/index.html`
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java`
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- Inline JavaScript syntax check via Node: passed.
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 4 tests passed.
- `mvn test`: 101 tests passed, 0 failures, 0 errors.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
- `smartkb-app`: healthy.
- `http://localhost:8082/actuator/health`: `UP`.
- Served homepage contains `workspaceNavMemory`.

Not verified:
- Browser click-through screenshot smoke was not rerun in this step.

Next step:
- Continue Project Intake / Code Context display polish or add browser click-through smoke for the new memory workspace.

## 2026-06-18 Work Log - Memory Workspace Browser Smoke

Current goal:
- Verify the new Memory workspace in a real browser against the running Docker app.

Completed:
- Confirmed `smartkb-app` was already running healthy in Docker.
- Confirmed `http://localhost:8082/actuator/health` returned `UP`.
- Attempted Dockerized Playwright smoke with `mcr.microsoft.com/playwright:v1.61.0-noble`; the image was not available locally and the run timed out while pulling/starting before a browser test executed.
- Added a temporary ignored CDP smoke script under `target/` and drove the installed local Chrome in headless mode.
- Clicked through all six workspaces: 智能问答, 项目接管, 任务状态, 记忆层, 代码上下文, Eval 评测.
- Verified the active menu and visible panel for each workspace.
- Verified workspace switching kept `window.scrollY=0` and did not create horizontal page overflow.
- Created a manual MemoryRecord through the Memory workspace and verified the new content appeared in the browser list.
- Updated `SPEC.md` to mark the browser click smoke complete.

Modified files:
- `SPEC.md`
- `HANDOFF.md`

Temporary files:
- `target/memory-workspace-docker-smoke.js`
- `target/memory-workspace-cdp-smoke.js`

Verified:
- `node .\target\memory-workspace-cdp-smoke.js`: passed.
- Smoke output included `ok=true`, all six workspace nav IDs, `memoryFound=true`, `scrollY=0`, and `overflow=false`.

Not verified:
- Mobile viewport screenshot smoke.

Next step:
- Continue SmartKB v2 Agent platform polish, likely Project Intake / Code Context display density or mobile screenshot smoke.

## 2026-06-18 Work Log - Mobile Workbench Layout

Current goal:
- Fix and verify the SmartKB workbench on a 390px mobile viewport.

Problem found:
- Before the fix, the fixed desktop sidebar kept `w-80` on mobile. At 390px width, the sidebar consumed 320px and the main workspace was squeezed to 70px.

Completed:
- Added mobile responsive rules for the static workbench.
- On small screens, the sidebar becomes a top area and the workspace nav becomes a compact 3-column grid.
- Hidden the uploaded-document list/status area on small screens so the workspace can use the viewport.
- Kept chat composer controls stacked on mobile and full-width.
- Added semantic layout classes such as `app-shell`, `app-sidebar`, `app-main`, `workspace-header`, and `chat-composer-row`.
- Added static regression coverage for the mobile layout media query and required container classes.
- Rebuilt and restarted `smartkb-app` from Docker Compose.
- Captured and visually checked mobile screenshots for chat and memory workspaces under `target/`.

Modified files:
- `src/main/resources/static/index.html`
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java`
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Temporary files:
- `target/mobile-workbench-cdp-smoke.js`
- `target/mobile-workbench-chat.png`
- `target/mobile-workbench-memory.png`

Verified:
- Inline JavaScript syntax check via Node: passed.
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 5 tests passed.
- `mvn test`: 102 tests passed, 0 failures, 0 errors.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
- `smartkb-app`: healthy.
- `http://localhost:8082/actuator/health`: `UP`.
- `node .\target\mobile-workbench-cdp-smoke.js`: passed at 390x844.
- Mobile smoke covered all six workspaces and confirmed sidebar/main/panel widths are 390px with no horizontal overflow.
- `node .\target\memory-workspace-cdp-smoke.js`: passed on desktop and still verified Memory create/list.

Not verified:
- Mobile form submission flows such as Project Intake submit and Code Context query.

Next step:
- Continue Project Intake / Code Context display polish or add mobile form interaction smoke.

## 2026-06-18 Work Log - Mobile Form Interaction Smoke

Current goal:
- Verify real mobile form interactions for Project Intake and Code Context.

Problem found:
- A fresh headless Chrome profile sometimes loaded the page before Tailwind CDN utilities were available.
- Without a local `.hidden` rule, workspace panels could visually stack because JavaScript toggled the `hidden` class but no CSS made it `display:none`.

Completed:
- Added a local `.hidden { display: none !important; }` fallback in `src/main/resources/static/index.html`.
- Extended `StaticWorkbenchHtmlTest` to guard the `.hidden` fallback.
- Ran a mobile CDP form smoke at 390x844 against the running Docker app.
- Submitted Project Intake with `/workspace/projects/smartkb-java21-spring-ai-rag` and verified the takeover result rendered.
- Ran Code Context keyword search for `StaticWorkbenchHtmlTest` and verified matching results rendered.
- Captured and visually checked mobile form screenshots under `target/`.
- Updated README, SPEC, and HANDOFF verification records.

Modified files:
- `src/main/resources/static/index.html`
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java`
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Temporary files:
- `target/mobile-form-cdp-smoke.js`
- `target/mobile-form-project-intake.png`
- `target/mobile-form-code-context.png`

Verified:
- Inline JavaScript syntax check via Node: passed.
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 5 tests passed.
- `mvn test`: 102 tests passed, 0 failures, 0 errors.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
- `smartkb-app`: healthy.
- `node .\target\mobile-form-cdp-smoke.js`: passed.
- `node .\target\memory-workspace-cdp-smoke.js`: passed.

Not verified:
- AgentTask / Eval mobile form interaction smoke.

Next step:
- Continue Project Intake / Code Context display polish, or add AgentTask / Eval mobile form interaction smoke.

## 2026-06-18 Work Log - AgentTask Eval Mobile Smoke

Current goal:
- Verify mobile form interactions for AgentTask and Eval at 390x844.

Completed:
- Ran a temporary CDP smoke script against the running Docker app.
- Created an AgentTask from the mobile workbench.
- Transitioned the AgentTask from `INTAKE` to `PLAN` with a plan note.
- Created an Eval run with a unique `E-MOBILE-*` case id.
- Verified the Eval run appeared in the run list.
- Verified the Eval report rendered after the new run.
- Captured and visually checked mobile screenshots under `target/`.
- Updated README, SPEC, and HANDOFF verification records.

Modified files:
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Temporary files:
- `target/mobile-agent-eval-cdp-smoke.js`
- `target/mobile-agent-task.png`
- `target/mobile-eval-list.png`
- `target/mobile-eval-report.png`

Verified:
- `node .\target\mobile-agent-eval-cdp-smoke.js`: passed.
- Smoke output confirmed AgentTask create/transition, Eval create/list/report, and no horizontal overflow.

Not verified:
- Fine-grained mobile edge cases such as long text wrapping and validation errors.

Next step:
- Continue Project Intake / Code Context display density polish, or add narrow edge-case smoke only if needed.

## 2026-06-18 Work Log - Project Intake Code Context Summary Metrics

Current goal:
- Improve the scan density of Project Intake and Code Context results.

Completed:
- Added reusable `renderCompactMetric(label, value, tone)` helper.
- Added a Project Intake summary strip for stack count, runnable-command count, verification-gap count, and warning count.
- Added a Code Context summary strip for result count, skipped-file count, warning count, and Git repository status.
- Kept existing detailed sections and backend API contracts unchanged.
- Added static regression coverage for the new summary helper.
- Updated README, SPEC, and HANDOFF.

Modified files:
- `src/main/resources/static/index.html`
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java`
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Temporary files:
- `target/project-code-summary-cdp-smoke.js`

Verified:
- Inline JavaScript syntax check via Node: passed.
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 5 tests passed.
- `mvn test`: 102 tests passed, 0 failures, 0 errors.
- `node .\target\project-code-summary-cdp-smoke.js`: passed against local `index.html` with mocked Project Intake / Code Context API responses.
- Mocked browser smoke confirmed Project Intake and Code Context summary metrics rendered and did not create horizontal overflow.

Not verified:
- Docker runtime rebuild for this exact change. `docker compose up -d --no-deps --build --force-recreate smartkb-app` timed out after 5 minutes.
- After stopping stuck docker/compose client processes, Docker API still timed out on `docker version`.
- The existing running app stayed healthy at `http://localhost:8082/actuator/health`, but the served homepage was still the previous image and did not include `renderCompactMetric`.

Next step:
- When Docker API responds again, rebuild only `smartkb-app`, confirm the served homepage contains `renderCompactMetric`, then rerun Project Intake / Code Context browser smoke against `http://localhost:8082`.

## 2026-06-18 Work Log - Workbench Summary Smoke Script

Current goal:
- Make the Project Intake / Code Context summary metric browser smoke repeatable outside ignored `target/` files.

Completed:
- Added `scripts/smoke/workbench-summary-smoke.mjs`.
- The script opens Chrome headless through CDP, loads either local `index.html` or a provided URL, mocks Project Intake / Code Context API responses, and verifies summary card counts, metric values, visibility, and 390px horizontal overflow.
- Documented the command in README, SPEC, TESTING, and HANDOFF.
- Rechecked Docker API after the takeover; it still timed out on `docker version`.
- Confirmed the existing `http://localhost:8082/actuator/health` endpoint stayed `UP`.
- Confirmed the currently served Docker homepage is still the previous image and does not contain `renderCompactMetric`.

Modified files:
- `scripts/smoke/workbench-summary-smoke.mjs`
- `README.md`
- `SPEC.md`
- `TESTING.md`
- `HANDOFF.md`

Verified:
- `node .\scripts\smoke\workbench-summary-smoke.mjs`: passed.
- Smoke output showed Project Intake metrics `3,2,1,0`, Code Context metrics `2,1,1,yes`, and no horizontal overflow.
- Local commit `test: add workbench summary smoke` was created.

Not verified:
- Docker runtime rebuild for the summary metrics remains blocked by the Docker API timeout.
- Push to `origin/main` succeeded after a later HTTPS retry; latest local commits are on GitHub.

Next step:
- When Docker API recovers, rebuild only `smartkb-app`, confirm the served homepage includes `renderCompactMetric`, then run `node .\scripts\smoke\workbench-summary-smoke.mjs "http://localhost:8082/?v=summary-smoke"`.

## 2026-06-18 Work Log - Summary Metrics Docker Runtime Verification

Current goal:
- Retry the previously blocked Docker runtime verification for Project Intake / Code Context summary metrics.

Completed:
- Started Docker Desktop after confirming the Docker Desktop service was stopped and no Docker API pipe existed.
- Rebuilt and recreated only `smartkb-app` with `docker compose up -d --no-deps --build --force-recreate smartkb-app`.
- Started existing PostgreSQL and Redis containers with `docker compose up -d postgres redis` after the app reported DNS failures for `postgres` and `redis`.
- Restarted `smartkb-app` after PostgreSQL and Redis became healthy.
- Confirmed the served Docker homepage contains `renderCompactMetric` and `workspaceNavMemory`.

Verified:
- Docker API recovered: `docker version` returned `29.5.3`.
- `smartkb-postgres`: healthy.
- `smartkb-redis`: healthy.
- `smartkb-app`: healthy.
- `http://localhost:8082/actuator/health`: `UP`.
- `node .\scripts\smoke\workbench-summary-smoke.mjs "http://localhost:8082/?v=summary-runtime-smoke"`: passed.
- Runtime smoke output showed Project Intake metrics `3,2,1,0`, Code Context metrics `2,1,1,yes`, and no horizontal overflow at 390px.

Modified files:
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Not verified:
- K3s/K3d real cluster deployment remains pending.

Next step:
- Continue SmartKB v2 Agent platform polish or run a focused mobile edge-case smoke if needed.

## 2026-06-18 Work Log - Mobile Edge Smoke Script

Current goal:
- Add repeatable browser coverage for narrow mobile edge cases that are easy to miss manually.

Completed:
- Extracted shared Chrome CDP helpers into `scripts/smoke/lib/chrome-cdp.mjs`.
- Updated `scripts/smoke/workbench-summary-smoke.mjs` to reuse the shared helper.
- Added `scripts/smoke/workbench-mobile-edge-smoke.mjs`.
- The new smoke opens a 390x844 mobile viewport and checks Project Intake, AgentTask, Memory, Code Context, and Eval edge states.
- Covered required-field validation messages, long single-token inputs, long textareas, workspace nav buttons, visible action buttons, and page-level horizontal overflow.
- Updated README, SPEC, TESTING, and HANDOFF.

Verified:
- `node --check .\scripts\smoke\lib\chrome-cdp.mjs`: passed.
- `node --check .\scripts\smoke\workbench-summary-smoke.mjs`: passed.
- `node --check .\scripts\smoke\workbench-mobile-edge-smoke.mjs`: passed.
- `node .\scripts\smoke\workbench-summary-smoke.mjs`: passed.
- `node .\scripts\smoke\workbench-mobile-edge-smoke.mjs`: passed on local `index.html`.
- `node .\scripts\smoke\workbench-mobile-edge-smoke.mjs "http://localhost:8082/?v=mobile-edge-smoke"`: passed against Docker runtime.

Modified files:
- `scripts/smoke/lib/chrome-cdp.mjs`
- `scripts/smoke/workbench-summary-smoke.mjs`
- `scripts/smoke/workbench-mobile-edge-smoke.mjs`
- `README.md`
- `SPEC.md`
- `TESTING.md`
- `HANDOFF.md`

Not verified:
- K3s/K3d real cluster deployment remains pending.

Next step:
- Continue SmartKB v2 Agent platform polish, with K3s/K3d verification as the remaining larger environment task.

## 2026-06-18 Work Log - K3s Manifest Offline Guard

Current goal:
- Continue toward K3s readiness without requiring user intervention.

Completed:
- Attempted disposable K3d verification using a temporary binary under `target/tools`.
- Confirmed Docker is available and `kubectl` exists, but no usable Kubernetes context is configured.
- GitHub release metadata for k3d was reachable, but downloading `k3d-windows-amd64.exe` timed out twice and produced incomplete files, so no cluster was created.
- Updated `k8s/k3s-demo.yaml` to set PostgreSQL `PGDATA=/var/lib/postgresql/data/pgdata`, avoiding common PVC root-directory initialization failures.
- Added `K3sDemoManifestTest` to parse `k8s/k3s-demo.yaml` with SnakeYAML and guard key deployment contracts offline.
- Updated K3s docs, README, SPEC, and HANDOFF.

Verified:
- `npx --yes js-yaml k8s/k3s-demo.yaml`: passed.
- `mvn -Dtest=K3sDemoManifestTest test`: passed.

Not verified:
- Disposable K3s/K3d runtime deployment remains blocked until a K3d/kind binary or another reachable Kubernetes cluster is available.

Next step:
- If K3d/kind becomes available, create a disposable cluster, import `smartkb:local`, apply `k8s/k3s-demo.yaml`, and verify `/actuator/health` through port-forward.

## 2026-06-18 Work Log - Memory Summary Metrics

Current goal:
- Improve Memory workspace scan density without changing backend APIs.

Completed:
- Added `renderMemorySummary(records)` to show total memories, high-authority count, source-type count, and unique tag count.
- Added small helpers for unique memory values and tags.
- Updated mobile edge smoke to mock Memory records and assert summary metrics `3,2,2,3`.
- Updated README, SPEC, and HANDOFF.

Verified:
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 5 tests passed.
- `node --check .\scripts\smoke\workbench-mobile-edge-smoke.mjs`: passed.
- `node .\scripts\smoke\workbench-mobile-edge-smoke.mjs`: passed on local `index.html`.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
- `smartkb-app`: healthy.
- `node .\scripts\smoke\workbench-mobile-edge-smoke.mjs "http://localhost:8082/?v=memory-summary-edge-rebuilt"`: passed.
- `node .\scripts\smoke\workbench-summary-smoke.mjs "http://localhost:8082/?v=memory-summary-regression"`: passed.
- `http://localhost:8082/actuator/health`: `UP`.

Modified files:
- `src/main/resources/static/index.html`
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java`
- `scripts/smoke/workbench-mobile-edge-smoke.mjs`
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Next step:
- Continue SmartKB v2 Agent platform information-density polish, or do the K3s/K3d runtime verification when a cluster tool is available.

## 2026-06-18 Work Log - Eval Run Summary Metrics

Current goal:
- Improve Eval workspace scan density without changing backend APIs.

Completed:
- Added `renderEvalRunSummary(runs)` to show total runs, passed runs, partial runs, and failed runs.
- Added `countEvalRunsByStatus(runs, status)` for normalized status counting.
- Extended `scripts/smoke/workbench-summary-smoke.mjs` to mock Eval runs/report and assert summary metrics `3,1,1,1`.
- Updated README, SPEC, and HANDOFF.

Modified files:
- `src/main/resources/static/index.html`
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java`
- `scripts/smoke/workbench-summary-smoke.mjs`
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 5 tests passed.
- `node --check .\scripts\smoke\workbench-summary-smoke.mjs`: passed.
- `node .\scripts\smoke\workbench-summary-smoke.mjs`: passed on local `index.html`; Project Intake metrics `3,2,1,0`, Code Context metrics `2,1,1,yes`, Eval metrics `3,1,1,1`.
- `mvn test`: 103 tests passed.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
- `smartkb-app`: healthy.
- `http://localhost:8082/actuator/health`: `UP`.
- Served homepage contains `renderEvalRunSummary`.
- `node .\scripts\smoke\workbench-summary-smoke.mjs "http://localhost:8082/?v=eval-summary-runtime"`: passed.

## 2026-06-18 Work Log - AgentTask Summary Metrics

Current goal:
- Improve AgentTask workspace scan density without changing backend APIs.

Completed:
- Added `renderAgentTaskSummary(tasks)` to show total tasks, active tasks, recorded tasks, and tasks needing intervention.
- Added `countAgentTasksByStatus(tasks, statuses)` for normalized status counting.
- Extended `scripts/smoke/workbench-summary-smoke.mjs` to mock AgentTask records and assert summary metrics `4,2,1,1`.
- Updated README, SPEC, and HANDOFF.

Modified files:
- `src/main/resources/static/index.html`
- `src/test/java/com/smartkb/StaticWorkbenchHtmlTest.java`
- `scripts/smoke/workbench-summary-smoke.mjs`
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- `mvn -Dtest=StaticWorkbenchHtmlTest test`: 5 tests passed.
- `node --check .\scripts\smoke\workbench-summary-smoke.mjs`: passed.
- `node .\scripts\smoke\workbench-summary-smoke.mjs`: passed on local `index.html`; AgentTask metrics `4,2,1,1`.
- `mvn test`: 103 tests passed.
- `docker compose up -d --no-deps --build --force-recreate smartkb-app`: passed.
- `smartkb-app`: healthy.
- `http://localhost:8082/actuator/health`: `UP`.
- Served homepage contains `renderAgentTaskSummary`.
- `node .\scripts\smoke\workbench-summary-smoke.mjs "http://localhost:8082/?v=agent-summary-runtime"`: passed.

## 2026-06-18 Work Log - K3d Runtime Retry

Attempted:
- Checked local tools: `kubectl` client exists, but there is no current Kubernetes context.
- Checked local tools: `k3d` and `kind` are not installed.
- Retried downloading `k3d-windows-amd64.exe` v5.8.3 into ignored `target/tools/k3d.exe`.

Result:
- Download timed out after several minutes with a partial file of about 3 MB.
- Stopped the lingering `curl.exe` process that held the partial file.
- Removed the incomplete `target/tools/k3d.exe`.
- No K3d cluster was created.
- Git working tree remained clean before this HANDOFF note.

Still blocked:
- True K3s/K3d runtime verification needs a usable `k3d`/`kind` binary or an existing Kubernetes context.

## 2026-06-18 Work Log - K3d Runtime Verification

Current goal:
- Verify `k8s/k3s-demo.yaml` against a real disposable local K3d cluster.

Completed:
- Installed `k3d` v5.9.0 through `winget` after direct GitHub release downloads failed.
- Created disposable cluster `smartkb-demo` with K3s v1.35.5+k3s1.
- Tagged the Docker Compose app image as `smartkb:local`.
- Imported `smartkb:local` into the cluster.
- Created namespace `smartkb` and `smartkb-secrets` with placeholder demo values.
- Applied `k8s/k3s-demo.yaml`.
- Verified PostgreSQL, Redis, and SmartKB pods reached `Running`.
- Verified PostgreSQL and Redis PVCs reached `Bound`.
- Verified rollouts for `postgres`, `redis`, and `smartkb-app`.
- Verified `/actuator/health` through temporary port-forward returned `UP` with `db`, `redis`, and `diskSpace` all `UP`.
- Verified `/api/agent/eval/report` returned successfully without requiring an LLM call.

Important notes:
- The generated K3d kubeconfig used `https://host.docker.internal:<port>`, which failed TLS handshake on this Windows environment.
- A temporary kubeconfig under ignored `target/` with the server URL changed to `https://127.0.0.1:<port>` fixed kubectl access.
- Initial Docker Hub pulls inside K3s had transient EOF errors, but retries succeeded.
- App startup had early restarts while PostgreSQL/Redis were not ready, then stabilized after dependencies came up.

Modified files:
- `docs/K3S_DEPLOYMENT_PLAN.md`
- `k8s/README.md`
- `README.md`
- `SPEC.md`
- `HANDOFF.md`

Cleanup:
- Deleted disposable `smartkb-demo` cluster after verification.
- Removed temporary kubeconfig and port-forward logs under ignored `target/`.
- `k3d` v5.9.0 remains installed through `winget` for future local K3d checks.

## 2026-06-18 Work Log - K8s Manifest Naming

Current goal:
- Remove ambiguity between the verified local K3d demo manifest and the older unverified Kubernetes draft.

Completed:
- Renamed `k8s/deployment.yaml` to `k8s/deployment-draft.yaml`.
- Added a top-of-file warning that `deployment-draft.yaml` must not be applied directly.
- Replaced draft Secret placeholder values with non-usable `replace-at-deploy-time` markers.
- Updated `docs/K3S_DEPLOYMENT_PLAN.md` to treat `k8s/k3s-demo.yaml` as the verified local demo entry point.
- Updated `k8s/README.md` so deploy commands apply `k8s/k3s-demo.yaml` and secrets are created at deploy time.
- Marked the naming cleanup in `SPEC.md`.

Modified files:
- `k8s/deployment-draft.yaml`
- `k8s/README.md`
- `docs/K3S_DEPLOYMENT_PLAN.md`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- `npx --yes js-yaml k8s/k3s-demo.yaml`: passed.
- `npx --yes js-yaml k8s/deployment-draft.yaml`: passed.
- `mvn -Dtest=K3sDemoManifestTest test`: passed.
- Markdown links in README/K3s docs: passed.
- `git diff --check`: passed with CRLF warnings only.

## 2026-06-18 Work Log - K8s Draft Guard Test

Current goal:
- Add regression coverage so the old Kubernetes draft is not mistaken for the verified demo manifest again.

Completed:
- Added `K8sDraftManifestTest`.
- Guarded that `k8s/deployment-draft.yaml` remains marked as a draft and says not to apply it directly.
- Guarded that draft Secret placeholders use non-usable `replace-at-deploy-time` markers.
- Guarded that `k8s/README.md` applies `k8s/k3s-demo.yaml`, not the old `k8s/deployment.yaml`.
- Marked the guard in `SPEC.md`.

Modified files:
- `src/test/java/com/smartkb/K8sDraftManifestTest.java`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- `mvn "-Dtest=K8sDraftManifestTest,K3sDemoManifestTest" test`: 3 tests passed.
- `mvn test`: 105 tests passed.
- `git diff --check`: passed with CRLF warnings only.

## 2026-06-18 Work Log - README Verification Status Refresh

Current goal:
- Keep the GitHub-facing README aligned with the latest verification state after the K8s draft guard test.

Completed:
- Updated README full test count from 103 to 105.
- Added a README bullet for `K8sDraftManifestTest` covering the draft warning, placeholder Secret markers, and verified README deploy entry point.

Modified files:
- `README.md`
- `HANDOFF.md`

Verified:
- Documentation-only change; no application tests required.
- `git diff --check`: passed with CRLF warnings only.
- `git diff --check`: passed with CRLF warnings only.

## 2026-06-18 Work Log - Project Intake Latest Snapshot Parsing

Current goal:
- Make Project Intake consume the newer English `HANDOFF.md` Latest Snapshot format before older historical sections.

Completed:
- Added `ProjectIntakeTextExtractor.labeledBullets(...)`.
- Updated `ProjectIntakeService` to prefer `Latest Snapshot` values for current goal, current stage, recently completed items, and next step only.
- Added regression coverage for a HANDOFF containing both a fresh Latest Snapshot and stale historical sections.
- Marked the capability in `SPEC.md`.

Modified files:
- `src/main/java/com/smartkb/agent/application/ProjectIntakeTextExtractor.java`
- `src/main/java/com/smartkb/agent/application/ProjectIntakeService.java`
- `src/test/java/com/smartkb/agent/application/ProjectIntakeServiceTest.java`
- `SPEC.md`
- `HANDOFF.md`

Verified:
- `mvn "-Dtest=ProjectIntakeServiceTest,ProjectIntakeTextExtractorTest" test`: 8 tests passed.
- `mvn test`: 106 tests passed.
- `git diff --check`: passed with CRLF warnings only.

## 2026-06-18 Work Log - Project Intake Design Sync

Current goal:
- Keep Project Intake API design documentation aligned with Latest Snapshot parsing behavior.

Completed:
- Updated `docs/PROJECT_INTAKE_API_DESIGN.md` output field contract to list `HANDOFF.md` `Latest Snapshot` as the highest-priority source.
- Documented supported labels: `Current goal:`, `Current stage:`, `Recently completed:`, and `Next step only:`.

Modified files:
- `docs/PROJECT_INTAKE_API_DESIGN.md`
- `HANDOFF.md`

Verified:
- Documentation-only change; no application tests required.
- `git diff --check`: passed with CRLF warnings only.

## 2026-06-18 Work Log - Agent Platform Next Step Refresh

Current goal:
- Remove stale Phase F next-step guidance from the Agent Platform spec.

Completed:
- Updated `docs/AGENT_PLATFORM_SPEC.md` section 10 to state that Phase F is closed.
- Redirected the recommended next step to small, verifiable SmartKB v2 Agent platform polish.
- Kept complex multi-agent orchestration explicitly out of scope.

Modified files:
- `docs/AGENT_PLATFORM_SPEC.md`
- `HANDOFF.md`

Verified:
- Documentation-only change; no application tests required.
- `git diff --check`: passed with CRLF warnings only.
