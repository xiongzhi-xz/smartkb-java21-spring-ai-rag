# SmartKB SPEC

## 当前目标

把 SmartKB 打磨成一个可本地运行、可演示、可解释的 Java 21 + Spring AI RAG 项目。当前优先级是先稳定完成演示闭环：

```text
Docker 启动 PostgreSQL/Redis -> Ollama 提供 Embedding -> IDEA 启动 Spring Boot
-> 上传测试文档 -> 普通对话流式回答 -> Advanced RAG 指定文档回答 -> 展开引用片段
```

## 已完成

- [x] PostgreSQL + pgvector 向量存储
- [x] Redis 基础设施
- [x] Ollama `nomic-embed-text` 本地 Embedding
- [x] Spring Boot `hybrid` 本地运行模式
- [x] 文档上传、解析、切片、向量入库
- [x] 文档列表、详情、删除、统计接口
- [x] 普通 RAG 问答
- [x] 多轮对话
- [x] 对话流式输出
- [x] 对话发送后等待反馈
- [x] Advanced RAG 查询改写
- [x] Advanced RAG 分阶段反馈（查询改写、检索、过滤、重排序、生成）
- [x] Advanced RAG 阶段耗时指标（改写、召回、过滤、重排序、生成、总耗时）
- [x] Advanced RAG 按文档过滤
- [x] Advanced RAG 引用片段展示
- [x] Advanced RAG 引用片段点击定位到文档详情 chunk
- [x] 已上传文档详情窗口可拖动调整宽度
- [x] Markdown/TXT 按 UTF-8 读取，避免中文乱码
- [x] Advanced RAG 双路召回、过滤下推、关键词重排序
- [x] Advanced RAG 锚点词重排序，优先命中查询改写、引用片段等核心章节
- [x] Advanced RAG Hybrid Search 基础版：向量召回 + 关键词召回融合
- [x] 前端文档删除入口与后端删除 SQL 修复
- [x] 改进与问题复盘文档 `docs/IMPROVEMENTS_AND_ISSUES.md`
- [x] 大文档演示语料 `test-docs/advanced-rag-demo.md`
- [x] Redis 会话记忆持久化（自研 RedisChatMemory）
- [x] Advanced RAG 接入 ChatMemory 体系（conversationId 统一管理）
- [x] 新建会话清空 ChatMemory（前端 + 后端 DELETE API）
- [x] 两种模式共享同一 conversationId（刷新/重启后可恢复会话）
- [x] AI 模型切换到 DeepSeek（deepseek-v4-flash）+ 异常友好提示
- [x] Prometheus 自定义指标（Micrometer + Counter/Timer）
- [x] Docker Compose 全链路一键启动（含 Spring Boot 容器）
- [x] Docker 构建收口：`.dockerignore` 控制上下文 + BuildKit Maven 缓存加速
- [x] Grafana Dashboard 预配置（数据源 + RAG 监控面板）

## Redis 会话记忆 — 面试讲法

### 核心问题
InMemoryChatMemory 只存在 JVM 内存，服务重启即丢失，不支持分布式共享。

### 解决方案
自研 `RedisChatMemory` 实现 Spring AI 的 `ChatMemory` 接口（3 个方法：add/get/clear）：
- **Redis List 结构**：`smartkb:chat:{conversationId}` 存储会话消息序列
- **TTL 过期**：默认 24 小时，活跃会话自动续期
- **JSON 序列化**：`{"type":"USER","content":"xxx"}`，避免 Java 序列化的安全风险和版本耦合
- **容错降级**：Redis 不可用时自动降级为 InMemoryChatMemory

### 为什么不用 Spring AI 官方 RedisChatMemory？
Spring AI 1.0.0-M1 版本没有提供 Redis ChatMemory 实现。ChatMemory 接口只有 3 个方法，
自研非常轻量，反而更能体现对接口设计和存储选型的理解。

### Advanced RAG 的 ChatMemory 改造
- 旧方案：前端 `buildHistoryText()` 拼接最近 10 条消息为纯文本字符串传给后端
- 新方案：后端通过 `conversationId` 从 ChatMemory 读取历史，写入 ChatMemory
- 好处：前端刷新/服务重启后会话可恢复；两种模式共享 conversationId

### 面试 40 分钟怎么讲
1. 先讲问题：InMemoryChatMemory 只存在内存，重启丢失
2. 再讲方案：ChatMemory 接口设计（add/get/clear）→ Redis List + TTL → 降级兜底
3. 然后讲 Advanced RAG 的改造：前端拼文本 → 后端 ChatMemory 统一管理
4. 最后讲延伸：Redis 除了会话记忆还能做缓存、限流、分布式锁

## 当前已知状态

- 本地服务启动方式：Docker Desktop 启动 PostgreSQL/Redis，Ollama 本机运行，Spring Boot 在 IDEA 中启动。
- SmartKB 主页面：`http://localhost:8080`
- 不是 Vue/Vite 独立前端，`http://localhost:3000` 不是主页面。
- 当前自动化测试通过：`mvn test`，共 103 个测试。
- Docker 构建已验证：构建上下文约 `11.08kB`，缓存命中后二次 `docker compose build smartkb-app` 约 1.5 秒，`docker compose up -d --no-deps --build --force-recreate smartkb-app` 可正常重建并通过健康检查。
- Docker 模式已挂载宿主机项目工作区：`SMARTKB_PROJECTS_ROOT` 默认 `..`，容器内使用 `/workspace/projects/<project-dir>` 做 Project Intake。
- 如果数据库里有修复前上传的 `advanced-rag-demo.md`，内容可能仍是乱码，需要删除后重新上传。
- Redis 会话记忆 Key 前缀：`smartkb:chat:`，TTL 默认 24 小时，可通过 `smartkb.chat-memory.ttl-hours` 配置。

## Redis 会话记忆验证清单

- [x] 重启 Spring Boot，确认日志显示 `初始化 ChatMemory (Redis 模式, TTL=24h)`
- [x] conversation 模式提问后，`redis-cli keys smartkb:chat:*` 能看到会话 Key
- [x] 刷新页面后同一 conversationId 追问，LLM 能记住之前的对话
- [x] 重启 Spring Boot 后同一 conversationId 追问，LLM 能记住之前的对话
- [x] Advanced 模式同样共享 conversationId，追问时查询改写能基于历史
- [x] 点击"新会话"后，Redis 中对应 Key 被删除，下次提问是新会话

自动化补充验证见：`docs/REDIS_CHAT_MEMORY_VERIFICATION.md`。`RedisChatMemoryIT` 已覆盖真实 Redis List、TTL、读取和清除行为，但不替代以上运行中应用 + 浏览器 + LLM 的 live 清单。

## 下一步开发

- [x] Embedding 接入 Ollama（nomic-embed-text 768 维，已合并到 application.yml）
- [x] K3s 部署方案（见 `docs/K3S_DEPLOYMENT_PLAN.md`）
- [x] K3s demo manifest 离线结构守卫测试（env、Secret、探针、Service、Ingress、PostgreSQL `PGDATA`）
- [x] Docker build 缓存优化与可复现重建验证（`.dockerignore` + BuildKit cache mount）
- [x] Docker 模式 Project Intake 宿主机项目只读挂载（容器内路径 `/workspace/projects/<project-dir>`）
- [x] RedisChatMemory 底层单元测试（写入、TTL、裁剪、读取、清除）
- [x] RedisChatMemory Testcontainers 集成测试与验证手册（见 `docs/REDIS_CHAT_MEMORY_VERIFICATION.md`）
- [x] Conversation 模式拆分独立 ChatMemory ChatClient，避免无文档命中时 QuestionAnswerAdvisor 压过 Redis 历史
- [x] ChatMemory 清理接口 WebMvcTest（DELETE `/api/chat/memory/{conversationId}` 成功和失败路径）
- [x] Conversation / Advanced RAG conversationId 契约 WebMvcTest（沿用传入 ID、缺省生成 ID）
- [x] Project Intake Markdown 提取器单元测试（section、列表、勾选项、去重与裁剪）
- [x] Project Intake 探测器单元测试（技术栈、构建工具、测试命令、证据类型）
- [x] Eval Report 聚合边界测试（失败原因归一化、排序、空指标汇总）
- [x] Eval Run 内存存储幂等保存测试（同 ID 更新且列表不重复）
- [x] MemoryRecord 输入与过滤归一化测试（projectId、sourceType、sourcePath、content、tags）
- [x] AgentTask 输入归一化与空字段保护测试（trim、默认 riskLevel、空 transition 不覆盖）
- [x] SmartKB v2 Agent 工程平台：Project Intake 前端最小入口（详见 `docs/PROJECT_INTAKE_API_DESIGN.md`）
- [x] Project Intake Web 层测试
- [x] Project Intake 本地端到端联调（`http://localhost:18080`，首页入口和 POST 接口已验证）
- [x] Project Intake 接管摘要质量增强（接管简报、技术栈证据、可运行命令、验证缺口）
- [x] Project Intake / Code Context 结果摘要指标（技术栈/命令/验证缺口/警告、结果/跳过/警告/Git）
- [x] Project Intake / Code Context 摘要指标浏览器 smoke 脚本（`scripts/smoke/workbench-summary-smoke.mjs`，本地静态页和 Docker 运行态首页均已通过）
- [x] 前端工作台布局整理（左侧工作区导航、独立功能面板、Docker 默认项目路径、中文化核心表单）
- [x] 前端工作台移动端响应式布局（顶部工作区入口、主面板完整宽度、无横向溢出）
- [x] 前端工作台 `.hidden` 样式兜底（Tailwind CDN 慢或失败时工作区面板仍能正确隐藏）
- [x] 前端工作台移动端表单交互 smoke（Project Intake 提交 + Code Context 查询）
- [x] 前端工作台移动端边界交互 smoke（长文本、错误提示、窄屏按钮/导航宽度，本地静态页和 Docker 运行态首页均已通过）
- [x] AgentTask / Eval 移动端表单交互 smoke（任务创建/流转 + Eval 新增/列表/报告）
- [x] AgentTask 状态模型设计与最小 API
- [x] AgentTask 前端状态面板（`http://localhost:18080`，首页入口和任务 API 已验证）
- [x] MemoryRecord 模型设计与最小 API
- [x] 从 SPEC/HANDOFF 生成高权威记忆
- [x] MemoryRecord 冲突提示规则
- [x] MemoryRecord 前端工作区（导入高权威、手工新增、列表、冲突检查）
- [x] MemoryRecord 前端工作区浏览器点击 smoke（6 个工作区切换 + 手工新增/列表）
- [x] MemoryRecord 列表摘要指标（总数、高权威、来源类型、标签数）
- [x] 代码上下文文件树索引与 rg 检索 API
- [x] Git diff 检索 API
- [x] 代码上下文 RAG chunk 提取 API
- [x] Code Context 前端面板
- [x] 代码上下文 RAG 语义补充
- [x] AgentTask / Eval 前端子 Tab 拆分，降低单页表单密度
- [x] 静态工作台 HTML 结构回归测试（工作区导航、子 Tab、ID 唯一、核心函数）
- [x] 静态工作台移动布局守卫测试（响应式容器 class 与 media query）
- [x] GitHub 展示首页 README 收口（RAG + Agent 双阶段叙事、启动、演示、API、验证状态）
- [x] Eval Case 运行记录 API
- [x] Eval 报告聚合 API
- [x] Eval 报告前端面板
- [x] Eval 运行记录摘要指标（总数、通过、部分、失败）
- [x] Eval 面试讲法总结
- [x] TicketRush E01-E10 结构化导入入口
- [x] Eval Run 持久化方案设计
- [x] Eval Run 可切换 JDBC 持久化实现
- [x] Eval Run JDBC 验证手册
- [x] Eval Run JDBC 本地 smoke test
- [x] Eval Run JDBC 自动化集成测试方案
- [x] Eval Run JDBC 集成测试 profile 与 Testcontainers 用例

## SmartKB v2 Agent 工程平台

SmartKB v2 的方向是把现有 RAG 项目升级为“面向 Java 存量项目的 AI Agent 接管与开发辅助平台”。

第一阶段不做完整多 Agent OS，先聚焦 5 个最小有竞争力能力：

- 项目接管 Agent：读取 Java 项目的 README、SPEC、AGENTS、HANDOFF、pom.xml、Git 状态和代码结构，输出接管摘要。
- 任务状态机：`Intake -> Plan -> Execute -> Verify -> Record`，避免长任务跑偏。
- 记忆分层：高权威记忆（SPEC/AGENTS/HANDOFF/架构决策）、中权威记忆（任务记录/验证结果）、低权威记忆（聊天摘要/临时观察）。
- 代码上下文检索：`rg`、Git diff、文件树和符号检索优先，向量检索作为语义补充。
- Eval 评测：使用 TicketRush 作为真实复杂 Java 项目样本，记录成功率、验证命令、耗时和人工介入次数。

详细规格见：`docs/AGENT_PLATFORM_SPEC.md`。
首批评测报告见：`docs/agent-eval-report.md`。
Project Intake 第一版接口设计见：`docs/PROJECT_INTAKE_API_DESIGN.md`。
Agent Task 状态机接口设计见：`docs/AGENT_TASK_API_DESIGN.md`。
Memory Record 记忆分层接口设计见：`docs/MEMORY_RECORD_API_DESIGN.md`。
Code Context 代码上下文接口设计见：`docs/CODE_CONTEXT_API_DESIGN.md`。
Eval Case 运行记录接口设计见：`docs/EVAL_CASE_RUN_API_DESIGN.md`。
Eval 面试讲法总结见：`docs/EVAL_INTERVIEW_SUMMARY.md`。
Eval Run 持久化方案见：`docs/EVAL_RUN_PERSISTENCE_DESIGN.md`，JDBC 验证手册见：`docs/EVAL_RUN_JDBC_VERIFICATION.md`，自动化集成测试方案见：`docs/EVAL_RUN_JDBC_INTEGRATION_TEST_PLAN.md`。

## 可观测性 — 面试讲法

### 指标设计
使用 Micrometer（Spring Boot Actuator 内置）注册自定义 Prometheus 指标：
- Counter：请求计数、成功/失败计数、文档上传计数
- Timer：RAG 各阶段耗时直方图（含 P50/P95/P99）、AI 调用耗时

### 为什么用 Micrometer 而不是手动打点？
Micrometer 是 Spring Boot 可观测性的标准抽象层，写一套代码能输出到
Prometheus/Datadog/CloudWatch，不绑定具体监控方案。

### 为什么看 P95/P99 不只看平均值？
平均值掩盖尾延迟；生产环境关心"95% 的请求在多少秒内完成"。

### Docker Compose 全链路一键启动
`docker compose up -d` 即可启动：PostgreSQL + Redis + Spring Boot + Prometheus + Grafana
Spring Boot 容器通过 Docker 内部 DNS 连接 PostgreSQL/Redis（不再依赖 localhost）
Grafana 自动配置数据源 + SmartKB RAG Dashboard

## 新对话接续方式

新对话先读：

```powershell
Get-Content -Raw SPEC.md
git log --oneline -5
```

然后优先从"Redis 会话记忆验证清单"继续。
