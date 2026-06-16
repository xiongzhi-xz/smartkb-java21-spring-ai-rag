# Project Intake API Design

## 1. 目标

Project Intake 是 SmartKB v2 Agent 平台的第一个后端能力：输入一个本地 Java 项目路径，读取项目文档、构建文件、Git 状态和关键目录，输出可被人直接接手的项目接管摘要。

第一版只做“理解项目”，不自动修改代码、不自动提交、不执行破坏性命令。

## 2. 设计依据

- `docs/AGENT_PLATFORM_SPEC.md`：定义项目接管 Agent、任务状态机、记忆分层、代码上下文检索和 eval 方向。
- `docs/agent-eval-report.md`：E01-E10 已验证 TicketRush 接管、链路解释、压测步骤、风险评审、运行数据判断和面试表达。
- `SPEC.md`：SmartKB 当前仍是 Spring Boot 静态前端 + Java 21 + Spring AI RAG 项目，Agent 平台是下一阶段能力。

## 3. 范围

### 做什么

- 接收本地项目路径和可选目标说明。
- 校验路径存在且是目录。
- 读取白名单文档和构建文件。
- 识别 Java/Maven/Spring Boot/Docker Compose/Git 基础信息。
- 读取 `git status --short` 和 `git log --oneline -5`。
- 生成结构化接管结果。
- 返回证据路径、风险点、建议下一步和读取日志。

### 不做什么

- 不改目标项目文件。
- 不提交目标项目。
- 不读取 `.env`、密钥、cookie、token、私钥内容。
- 不递归读取大文件、运行数据目录、构建产物目录。
- 不执行测试、构建、压测或服务启动。
- 不做多 Agent 编排。

## 4. API

### 4.1 接管项目

```http
POST /api/agent/projects/intake
Content-Type: application/json
```

请求：

```json
{
  "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
  "goal": "接管 TicketRush，判断下一步只做什么",
  "includeCodeTree": true,
  "maxFiles": 200,
  "maxFileBytes": 65536
}
```

字段说明：

| 字段 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `rootPath` | 是 | - | 本地项目根目录，第一版只允许本机路径 |
| `goal` | 否 | `接管项目并输出下一步` | 用户目标说明 |
| `includeCodeTree` | 否 | `true` | 是否读取 `src/main`、`src/test` 文件树 |
| `maxFiles` | 否 | `200` | 文件树最多返回文件数 |
| `maxFileBytes` | 否 | `65536` | 单文件读取上限，超过只记录文件存在 |

成功响应：

```json
{
  "success": true,
  "project": {
    "id": "ticket-rush-20260617-001",
    "name": "ticketrush-java21-high-concurrency",
    "rootPath": "E:/project/work/job/ticketrush-java21-high-concurrency",
    "detectedStack": ["Java 21", "Spring Boot", "Maven", "Docker Compose", "Redis", "RocketMQ"],
    "buildTool": "maven",
    "packageManagerEvidence": ["pom.xml"],
    "testCommands": ["mvn test", "mvn clean verify"],
    "createdAt": "2026-06-17T10:00:00+08:00"
  },
  "intake": {
    "currentGoal": "把 TicketRush 收口为可本地运行、可压测、可面试讲解的 Java 21 高并发票务秒杀系统。",
    "currentStage": "Docker Compose 全链路已验证，下一步是 k6 压测和真实数据报告。",
    "completed": [
      "抢票核心链路",
      "Redis Lua/Redis Lock/MySQL 乐观锁三种库存策略",
      "RocketMQ 异步下单",
      "Sentinel 限流",
      "Docker Compose 全链路",
      "Prometheus/Grafana"
    ],
    "unfinished": [
      "三种库存策略 k6 压测",
      "限流前后稳定性压测",
      "Virtual Threads 对比报告",
      "Seata 示例"
    ],
    "workingTree": {
      "isGitRepository": true,
      "hasUncommittedChanges": false,
      "statusShort": "",
      "latestCommits": [
        "38d7c1d fix: disable nacos config health noise in docker profile"
      ]
    },
    "risks": [
      "不要继续扩功能，当前优先级是收口验证和真实压测。",
      "不要提交 RocketMQ、MySQL、Redis、Elasticsearch 等本地运行数据。",
      "不要读取或复述 .env、token、cookie、私钥。"
    ],
    "nextStepOnly": "k6 对三种库存策略跑第一轮本地压测"
  },
  "evidence": [
    {
      "type": "handoff",
      "path": "HANDOFF.md",
      "summary": "包含当前目标、阶段、已完成、未验证、风险和下一步。"
    },
    {
      "type": "spec",
      "path": "SPEC.md",
      "summary": "包含阶段任务和未完成项。"
    },
    {
      "type": "git",
      "command": "git status --short",
      "summary": "工作区干净。"
    }
  ],
  "readLog": {
    "readFiles": ["HANDOFF.md", "SPEC.md", "README.md", "pom.xml", "docker-compose.yml"],
    "skippedFiles": [
      {
        "path": ".env",
        "reason": "sensitive file"
      },
      {
        "path": "target/",
        "reason": "build output"
      }
    ],
    "commands": ["git status --short", "git log --oneline -5"]
  }
}
```

失败响应：

```json
{
  "success": false,
  "code": "PROJECT_PATH_NOT_FOUND",
  "error": "项目路径不存在或不可访问"
}
```

## 5. 输出字段契约

| 字段 | 来源优先级 | 说明 |
| --- | --- | --- |
| `currentGoal` | `HANDOFF.md` > `SPEC.md` > `README.md` | 当前项目目标 |
| `currentStage` | `HANDOFF.md` > `SPEC.md` | 当前阶段 |
| `completed` | `HANDOFF.md` + `SPEC.md` 已完成勾选 | 已完成能力 |
| `unfinished` | `HANDOFF.md` 未验证/下一步 + `SPEC.md` 未完成勾选 | 待办列表 |
| `workingTree` | Git 命令 | 工作区和最近提交 |
| `risks` | `HANDOFF.md` 风险 + 安全规则 + Git 状态 | 接管风险 |
| `nextStepOnly` | 未完成任务优先级 | 下一步只做一件事 |
| `evidence` | 文件路径/命令 | 每个关键结论必须可追溯 |

## 6. 读取白名单

优先读取：

```text
README.md
SPEC.md
AGENTS.md
CLAUDE.md
HANDOFF.md
PROJECT.md
PROJECT_STATUS.md
TODO.md
CHANGELOG.md
pom.xml
build.gradle
settings.gradle
package.json
docker-compose.yml
Dockerfile
src/main
src/test
docs
```

默认跳过：

```text
.env
.env.*
*.key
*.pem
*.p12
*.jks
target
build
node_modules
.git
docker/**/store
docker-data
data
tmp
logs
*.log
```

敏感文件处理规则：

- 可以报告“文件存在”。
- 不读取内容。
- 不返回值。
- 不写入 eval 报告或记忆。

## 7. 状态机

Project Intake 任务固定流转：

```text
Intake -> Plan -> Execute -> Verify -> Record
```

第一版状态含义：

| 状态 | 动作 | 产物 |
| --- | --- | --- |
| `Intake` | 接收路径和目标，校验安全边界 | `ProjectIntakeRequest` |
| `Plan` | 计算读取清单和跳过清单 | `ProjectReadPlan` |
| `Execute` | 读取文件、文件树和 Git 状态 | `ProjectRawContext` |
| `Verify` | 检查关键字段是否有证据 | `ProjectIntakeValidation` |
| `Record` | 返回接管摘要，后续可落记忆 | `ProjectIntakeResponse` |

如果路径非法、Git 命令失败或核心文档为空，状态进入 `Record: failed`，响应必须说明失败原因。

## 8. 后端模块拆分

建议新增包：

```text
com.smartkb.agent
├── controller
│   └── ProjectIntakeController
├── application
│   └── ProjectIntakeService
├── domain
│   ├── ProjectContext
│   ├── ProjectIntakeResult
│   ├── ProjectEvidence
│   └── ProjectReadLog
└── infrastructure
    ├── filesystem
    │   ├── ProjectFileReader
    │   └── ProjectPathGuard
    └── git
        └── GitCommandReader
```

第一版不引入数据库。接口响应可直接由实时读取结果生成；后续再持久化 `ProjectContext`、`AgentTask`、`MemoryRecord`。

## 9. 错误码

| code | HTTP | 场景 |
| --- | --- | --- |
| `PROJECT_PATH_REQUIRED` | 400 | `rootPath` 为空 |
| `PROJECT_PATH_NOT_FOUND` | 400 | 路径不存在 |
| `PROJECT_PATH_NOT_DIRECTORY` | 400 | 路径不是目录 |
| `PROJECT_PATH_NOT_ALLOWED` | 400 | 路径不在允许范围内 |
| `PROJECT_FILE_READ_FAILED` | 500 | 白名单文件读取失败 |
| `PROJECT_GIT_COMMAND_FAILED` | 200 | Git 命令失败但文件读取成功，作为 warning 返回 |
| `PROJECT_INTAKE_INSUFFICIENT_CONTEXT` | 422 | 缺少 `README/SPEC/HANDOFF/pom.xml` 等核心上下文 |

Git 失败不应直接让接管失败，因为有些项目可能不是 Git 仓库；响应中用 `warnings` 记录。

## 10. TicketRush 验收样例

对 `E:/project/work/job/ticketrush-java21-high-concurrency` 调用 intake 后，至少应识别：

- 当前目标：可本地运行、可压测、可面试讲解的 Java 21 高并发票务系统。
- 当前阶段：Docker Compose 全链路已验证，下一步是 k6 压测和真实数据报告。
- 工作区状态：干净。
- 最新提交：`38d7c1d fix: disable nacos config health noise in docker profile`。
- 已完成：抢票链路、三种库存策略、RocketMQ、Sentinel、Docker Compose、Prometheus/Grafana、35 tests。
- 未完成：三种库存策略 k6 压测、限流前后稳定性压测、Virtual Threads 对比报告、Seata 示例。
- 风险：不要继续扩功能、不要提交运行数据、不要读取密钥。
- 下一步只做：k6 对三种库存策略跑第一轮本地压测。

## 11. 实现任务拆分

1. [x] 新增请求/响应 DTO 和 `ProjectIntakeController`。
2. [x] 新增 `ProjectPathGuard`，实现路径存在性、目录校验、敏感路径跳过。
3. [x] 新增 `ProjectFileReader`，按白名单读取文档和文件树。
4. [x] 新增 `GitCommandReader`，读取 `status --short` 和 `log --oneline -5`。
5. [x] 新增 `ProjectIntakeService`，汇总目标、阶段、已完成、未完成、风险和下一步。
6. [x] 增加单元测试：路径非法、非 Git 仓库、敏感文件跳过、TicketRush fixture 摘要。
7. [ ] 前端增加最小入口：项目路径输入、接管按钮、摘要/风险/证据面板。

## 12. 验证命令

实现后至少运行：

```powershell
mvn test
git diff --check
```

手动验证：

```powershell
Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/agent/projects/intake `
  -ContentType application/json `
  -Body '{"rootPath":"E:/project/work/job/ticketrush-java21-high-concurrency","goal":"接管 TicketRush，判断下一步只做什么"}'
```
