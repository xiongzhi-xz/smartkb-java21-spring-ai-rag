# SmartKB v2 Agent Platform SPEC

## 1. 项目定位

SmartKB v2 的目标不是继续做一个普通知识库，而是在现有 RAG 能力上升级为：

```text
面向 Java 存量项目的 AI Agent 接管与开发辅助平台
```

核心场景：

用户选择一个 Java 项目目录，例如 TicketRush，SmartKB 能读取项目文档、构建文件、Git 状态和代码结构，自动生成项目接管摘要、风险清单、下一步任务，并在后续迭代中支持任务状态流转、记忆沉淀、代码上下文检索和验证记录。

求职叙事：

- SmartKB v1 证明企业 RAG 工程化能力。
- SmartKB v2 证明 Agent 工程化能力。
- TicketRush 作为真实复杂 Java 项目样本，证明该 Agent 能接管真实工程，而不是只处理演示文档。

## 2. 目标岗位匹配

该阶段服务于以下岗位：

- AI 应用工程师
- Agent 工程化工程师
- RAG 平台工程师
- AI 后端工程师
- Java 后端 + 大模型应用方向

项目要回答面试官的核心疑问：

- 你是不是只会调 API？
- Agent 如何避免长任务跑偏？
- 上下文太长时如何管理？
- 记忆如何分层、晋级和冲突处理？
- 代码检索为什么不能只靠向量？
- 如何评测 Agent 是否真的有用？
- 你原来的 Java 后端经验如何迁移到 AI 工程化？

## 3. 最小有竞争力版本

第一阶段只做 5 个能力，不追求完整多 Agent OS。

### 3.1 项目接管 Agent

输入：

- 本地 Java 项目路径
- 可选目标说明，例如“接管 TicketRush 并判断下一步做什么”

读取范围：

- `README.md`
- `SPEC.md`
- `AGENTS.md`
- `HANDOFF.md`
- `pom.xml`
- `docker-compose.yml`
- `src/main`
- `src/test`
- `git status --short`
- `git log --oneline -5`

输出：

```text
当前目标：
当前阶段：
已完成：
未完成：
工作区是否有未提交改动：
风险点：
建议下一步只做：
```

验收标准：

- 能对 TicketRush 生成准确接管摘要。
- 能识别未提交改动、运行数据目录、构建命令和测试命令。
- 不需要直接改代码，先完成“理解项目”的可信输出。

### 3.2 任务状态机

所有 Agent 任务必须经过固定状态：

```text
Intake -> Plan -> Execute -> Verify -> Record
```

状态说明：

- `Intake`：读取用户目标、项目规则、当前 Git 状态。
- `Plan`：拆解文件范围、风险点、验证方式。
- `Execute`：执行代码或文档改动。
- `Verify`：运行测试、lint、build、接口检查或手动验证。
- `Record`：更新 `HANDOFF.md`、任务文档或验证记录。

约束：

- 高风险改动必须停在 `Plan` 等用户确认。
- 如果验证失败，状态不能进入 `Record: done`，只能记录为 `Record: blocked/failed`。
- 一次任务只允许一个主目标，避免“顺手优化”。

验收标准：

- 任意任务页面能看到当前状态。
- 状态流转有日志记录。
- 能解释“为什么 Agent 不会自由发散和跑偏”。

### 3.3 记忆分层

记忆不是简单聊天记录，必须按权威等级管理。

#### 高权威记忆

来源：

- `SPEC.md`
- `AGENTS.md`
- `HANDOFF.md`
- 架构决策文档
- 用户明确确认的规则

用途：

- 项目边界
- 技术选型
- 安全红线
- 验收标准

冲突处理：

- 高权威记忆优先于聊天摘要。
- 修改高权威记忆需要明确记录原因。

#### 中权威记忆

来源：

- 任务完成记录
- 验证结果
- 踩坑记录
- 评审结论

用途：

- 下次接管时恢复上下文。
- 避免重复踩坑。

#### 低权威记忆

来源：

- 临时聊天摘要
- Agent 的推断
- 未验证观察

用途：

- 辅助检索和提示。
- 不能直接覆盖项目规则。

验收标准：

- 能展示某条记忆的来源、权威等级和更新时间。
- 同一主题出现冲突时，能说明采用哪条以及原因。
- Redis 继续用于会话记忆，项目长期记忆可先落到 Markdown/数据库，后续再向量化。

### 3.4 代码上下文检索

代码项目不能只依赖向量检索。

检索优先级：

1. `rg` 精确关键词搜索。
2. Git diff 和最近提交。
3. 文件树和 Maven 模块结构。
4. Java 包名、类名、接口名、测试类名。
5. 向量检索作为语义补充。

面试讲法：

- 代码任务中，精确符号和路径比语义相似更可靠。
- 向量适合找“概念相近”的模块，但不能替代 `rg`。
- 最终上下文需要包含证据路径，而不是只给模型一段模糊摘要。

验收标准：

- 对 TicketRush 提问“RocketMQ 订单创建链路在哪些文件里”，能返回文件路径和简要职责。
- 对 TicketRush 提问“库存扣减有几种策略”，能返回 Redis Lua、Redis Lock、MySQL 乐观锁相关类和文档。
- 检索结果必须附带来源路径。

### 3.5 Eval 评测

用 TicketRush 作为真实评测项目，准备 10-20 个任务。

评测指标：

- 是否成功完成
- 是否需要人工介入
- 是否运行验证命令
- 测试是否通过
- 耗时
- 工具调用次数
- 失败原因

首批评测任务：

| 编号 | 任务 | 预期产出 |
| --- | --- | --- |
| E01 | 接管 TicketRush 项目 | 接管摘要 + 风险清单 |
| E02 | 解释 RocketMQ 异步下单链路 | 文件路径 + 调用流程 |
| E03 | 解释 Redis Lua 防超卖方案 | 核心类 + Lua 脚本 + 边界 |
| E04 | 判断 Docker Compose 启动前置条件 | JAR 构建要求 + 容器依赖 |
| E05 | 生成 k6 最小压测步骤 | 可执行步骤 + 验证指标 |
| E06 | 找出当前未完成任务 | 从 SPEC/HANDOFF 汇总 |
| E07 | 评审一次小改动风险 | 风险点 + 验证建议 |
| E08 | 补充一条文档验证记录 | 文档 diff + git status |
| E09 | 判断运行数据是否应提交 | `.gitignore` 建议 |
| E10 | 生成面试讲法 | 2 分钟版 + 追问点 |

验收标准：

- 至少完成 10 个任务记录。
- 形成 `docs/agent-eval-report.md`。
- 成功率、失败原因和人工介入次数必须如实记录，不美化数据。

## 4. 技术架构

### 4.1 后端模块建议

```text
com.smartkb.agent
├── controller        # Agent 接管、任务、记忆、eval API
├── application       # 任务编排、状态机、接管流程
├── domain            # ProjectContext、AgentTask、MemoryRecord、EvalCase
├── infrastructure
│   ├── git           # git status/log/diff 读取
│   ├── filesystem    # 文件树、文档读取、路径安全
│   ├── search        # rg/关键词/向量混合检索
│   └── memory        # Markdown/Redis/数据库记忆适配
└── prompt            # 接管摘要、任务规划、评审提示词
```

### 4.2 前端最小界面

当前仍沿用 Spring Boot 静态页面，不引入 Vue/Vite。

新增区域：

- 项目路径输入框
- “接管项目”按钮
- 接管摘要面板
- 风险清单面板
- 任务状态面板
- 记忆列表面板
- Eval 任务表格

### 4.3 数据模型草案

`ProjectContext`：

- `id`
- `name`
- `rootPath`
- `detectedStack`
- `buildTool`
- `testCommand`
- `lastGitStatus`
- `lastGitLog`
- `createdAt`
- `updatedAt`

`AgentTask`：

- `id`
- `projectId`
- `title`
- `goal`
- `status`
- `riskLevel`
- `plan`
- `verification`
- `resultSummary`
- `createdAt`
- `updatedAt`

`MemoryRecord`：

- `id`
- `projectId`
- `authorityLevel`
- `sourceType`
- `sourcePath`
- `content`
- `tags`
- `createdAt`
- `updatedAt`

`EvalCase`：

- `id`
- `projectId`
- `title`
- `input`
- `expectedOutput`
- `actualOutput`
- `success`
- `manualInterventionCount`
- `verificationCommand`
- `durationMillis`
- `failureReason`

## 5. API 草案

```http
POST /api/agent/projects/intake
```

用途：接管项目，生成项目摘要。

```json
{
  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
  "goal": "接管 TicketRush，判断下一步只做什么"
}
```

```http
GET /api/agent/projects/{projectId}
GET /api/agent/projects/{projectId}/memories
POST /api/agent/tasks
GET /api/agent/tasks/{taskId}
POST /api/agent/tasks/{taskId}/advance
POST /api/agent/search/code
POST /api/agent/eval/run
GET /api/agent/eval/report
```

## 6. 安全边界

- 默认只允许读取用户显式选择的项目目录。
- 禁止读取 `.env` 内容；只允许报告字段是否存在。
- 禁止打印 API key、token、cookie、私钥。
- 默认不执行删除、移动、重置 Git 等破坏性命令。
- 第一阶段不让 Agent 自动提交代码。
- 第一阶段不做云端多租户，不处理真实企业代码上传。
- 第一阶段不接入支付、生产数据库、真实权限系统。

## 7. 不做什么

第一阶段不做：

- 完整 Clowder 类多 Agent OS。
- 桌面端。
- 飞书/Telegram/GitHub PR 集成。
- 语音伴侣。
- 游戏模式。
- 自主长期运行的后台 Agent。
- 云端多租户 sandbox。
- 自动大规模改代码。
- 复杂模型路由和成本看板。

原因：

当前目标是求职竞争力和面试可讲性，优先做能和 Java 后端经验结合的最小闭环。

## 8. 分阶段任务

### 阶段 A：SPEC 与评测样本

产出：

- [x] `docs/AGENT_PLATFORM_SPEC.md`
- [x] `docs/agent-eval-report.md` 模板
- [x] TicketRush 10 个 eval case 初稿
- [x] TicketRush E01-E10 eval 执行记录（20/20）
- [x] `docs/PROJECT_INTAKE_API_DESIGN.md`

验收：

- 能说明 SmartKB v2 和普通 RAG 的差异。
- 能说明为什么 TicketRush 是真实评测样本。

### 阶段 B：项目接管 Agent

产出：

- [x] Project Intake API 设计文档
- [x] Project Intake 后端接口骨架
- [x] 项目路径输入（API 请求字段）
- [x] 文件读取白名单
- [x] Git status/log 读取
- [x] `README/SPEC/AGENTS/HANDOFF/pom.xml` 摘要
- [x] 接管摘要输出
- [x] 前端最小入口
- [x] Web 层测试
- [x] 本地端到端联调

验收：

- 对 TicketRush 生成准确接管摘要。
- 不读取或泄露 `.env` 内容。

### 阶段 C：任务状态机

产出：

- [x] AgentTask 状态模型
- [x] 状态流转 API
- [x] 状态流转记录
- [x] 失败/阻塞记录
- [x] 前端任务状态面板

验收：

- 能展示任务从 Intake 到 Record 的全过程。
- 失败任务不会被标记为完成。

### 阶段 D：记忆分层

产出：

- [x] MemoryRecord 模型
- [x] 高/中/低权威记忆列表
- [ ] 从 SPEC/HANDOFF 生成高权威记忆
- [ ] 冲突提示规则

验收：

- 能展示每条记忆来源和权威等级。
- 能解释高权威记忆优先。

### 阶段 E：代码上下文检索

产出：

- [ ] 文件树索引
- [ ] `rg` 关键词检索
- [ ] Git diff 检索
- [ ] RAG 语义补充
- [ ] 来源路径展示

验收：

- 能回答 TicketRush 关键链路文件位置。
- 输出包含文件路径和证据。

### 阶段 F：Eval 报告

产出：

- [ ] 10 个 TicketRush eval case 执行记录
- [ ] 成功率和失败原因统计
- [ ] 面试讲法总结

验收：

- 有真实数据，不只写“已完成”。
- 能回答“你怎么证明 Agent 有用”。

## 9. 面试讲法

30 秒版：

```text
SmartKB 最初是一个企业 RAG 知识库，我后来把它升级为面向 Java 存量项目的 Agent 工程平台。它不是只做文档问答，而是能读取真实 Java 项目的 README、SPEC、Git 状态、构建文件和代码结构，生成接管摘要，并通过任务状态机、记忆分层、代码检索和 eval 评测来解决 Agent 长任务容易跑偏、上下文丢失和不可验证的问题。我用 TicketRush 这个高并发票务项目作为真实样本来验证它。
```

2 分钟版：

```text
我做 SmartKB v2 的原因是，普通 RAG 项目只能证明会做知识问答，但 AI 应用工程真正难的是让 Agent 在真实工程里稳定工作。我的方案分成几层：第一层是项目接管，读取 README、SPEC、AGENTS、HANDOFF、pom.xml、Git 状态和代码结构，生成当前目标、阶段、已完成、未完成、风险点和下一步。第二层是任务状态机，所有任务必须经过 Intake、Plan、Execute、Verify、Record，避免 Agent 直接自由发挥。第三层是记忆分层，把 SPEC 和架构决策作为高权威记忆，任务记录作为中权威记忆，聊天摘要作为低权威记忆，冲突时按权威等级处理。第四层是代码上下文检索，代码场景优先用 rg、Git diff、文件树和类名，向量检索只做语义补充。最后我用 TicketRush 做 eval，记录任务成功率、测试通过率、耗时和人工介入次数。这个项目体现的是我把 Java 后端工程经验迁移到 Agent 工程化里的能力。
```

## 10. 当前下一步

建议下一步只做：

```text
为 MemoryRecord 增加从 SPEC/HANDOFF 生成高权威记忆的导入能力。
```

不要马上写复杂多 Agent 编排。先让高权威记忆从真实项目文档产生，再处理冲突提示规则。
