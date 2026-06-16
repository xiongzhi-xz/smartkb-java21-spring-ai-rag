# SmartKB v2 Agent Eval Report

## 1. 评测目标

本报告用于验证 SmartKB v2 是否具备“面向 Java 存量项目的 Agent 接管与开发辅助能力”。

评测不追求模型回答好听，而关注工程结果：

- 是否能准确理解真实 Java 项目。
- 是否能输出可执行的下一步。
- 是否能识别风险和未提交改动。
- 是否能基于文件路径给出证据。
- 是否能遵守任务状态机，不自由发散。
- 是否能记录验证结果和失败原因。

## 2. 评测项目

项目名称：TicketRush

本地路径：

```text
E:/project/work/job/ticketrush-java21-high-concurrency
```

选择 TicketRush 的原因：

- 它是一个真实复杂 Java 21 + Spring Boot 3 项目。
- 包含高并发抢票、Redis Lua、RocketMQ、Sentinel、MySQL、Docker Compose、Prometheus/Grafana 等工程内容。
- 有 `README.md`、`SPEC.md`、`AGENTS.md`、`HANDOFF.md`、测试、Docker 配置和最近提交记录。
- 能覆盖 AI Agent 接管项目时常见的理解、检索、验证和风险识别任务。

## 3. 评测环境

| 项目 | 内容 |
| --- | --- |
| SmartKB 版本 | 待填写 |
| TicketRush commit | `38d7c1d fix: disable nacos config health noise in docker profile` |
| JDK | 21 |
| Maven | 待填写 |
| Docker | 待填写 |
| 模型 | 待填写 |
| 执行方式 | 人工提示 / SmartKB UI / API |
| 评测日期 | 2026-06-17 |

## 4. 评测指标

| 指标 | 说明 |
| --- | --- |
| 成功 | 是否达到该 eval case 的预期产出 |
| 证据路径 | 输出是否包含文件路径、命令或文档来源 |
| 验证命令 | 是否运行或建议了合理验证命令 |
| 人工介入次数 | 人需要纠正、补充或阻止的次数 |
| 耗时 | 从任务开始到产出结束的时间 |
| 工具调用次数 | 文件读取、搜索、命令执行等次数 |
| 失败原因 | 未通过时必须记录真实原因 |

评分规则：

```text
2 分：产出准确，有证据路径，能直接用于下一步。
1 分：大方向正确，但缺证据、遗漏风险或需要明显人工修正。
0 分：结论错误、泛泛而谈、违反规则或无法用于下一步。
```

## 5. Eval Case 总览

| 编号 | 任务 | 状态 | 得分 | 人工介入 | 备注 |
| --- | --- | --- | --- | --- | --- |
| E01 | 接管 TicketRush 项目 | 通过 | 2 | 0 | 准确识别阶段、已完成/未完成、风险和下一步 |
| E02 | 解释 RocketMQ 异步下单链路 | 通过 | 2 | 0 | 找到发送、消费、订单幂等、重试配置、补偿和测试证据 |
| E03 | 解释 Redis Lua 防超卖方案 | 通过 | 2 | 0 | 找到 Lua 脚本、库存 Hash、幂等 Key、失败映射和策略差异 |
| E04 | 判断 Docker Compose 启动前置条件 | 通过 | 2 | 0 | 准确识别本地 JAR 挂载、核心依赖和健康验证方式 |
| E05 | 生成 k6 最小压测步骤 | 未执行 | - | - | - |
| E06 | 找出当前未完成任务 | 未执行 | - | - | - |
| E07 | 评审一次小改动风险 | 未执行 | - | - | - |
| E08 | 补充一条文档验证记录 | 未执行 | - | - | - |
| E09 | 判断运行数据是否应提交 | 未执行 | - | - | - |
| E10 | 生成面试讲法 | 未执行 | - | - | - |

## 6. Eval Case 详情

### E01 接管 TicketRush 项目

输入：

```text
接管 E:/project/work/job/ticketrush-java21-high-concurrency。
先不要改代码，读取 HANDOFF.md、PROJECT.md、SPEC.md、AGENTS.md、README.md、git status --short、git log --oneline -5。
输出当前目标、当前阶段、已完成、未完成、工作区是否有未提交改动、风险点和下一步只做什么。
```

预期产出：

- 能识别 TicketRush 是 Java 21 高并发票务秒杀项目。
- 能识别当前重点是收口验证、压测和作为 SmartKB v2 eval 样本。
- 能说明工作区状态。
- 能输出“下一步只做一件事”。

通过标准：

- 输出包含 `HANDOFF.md`、`SPEC.md` 和 git 状态证据。
- 不建议新增无关功能。
- 不执行代码修改。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 通过 |
| 得分 | 2 |
| 耗时 | 未单独计时 |
| 工具调用次数 | 5（读取 SmartKB/TicketRush 交接与评测文档，执行 TicketRush `git status --short`、`git log --oneline -5`） |
| 人工介入次数 | 0 |
| 实际产出摘要 | TicketRush 工作区干净，最新提交为 `38d7c1d fix: disable nacos config health noise in docker profile`。当前阶段是 Docker Compose 全链路已验证，下一步进入 k6 压测和真实数据报告。已完成抢票链路、Redis Lua/锁/MySQL 乐观锁、RocketMQ 异步下单、Sentinel、Docker Compose、Prometheus、Grafana，且 35 tests 通过。未完成三种库存策略 k6 压测、限流前后稳定性压测、Virtual Threads 对比报告、Seata 示例。风险点是不要继续扩功能、不要提交运行数据，当前最缺真实压测数据。下一步只做 k6 对三种库存策略跑第一轮本地压测。 |
| 失败原因 | 无 |

### E02 解释 RocketMQ 异步下单链路

输入：

```text
解释 TicketRush 的 RocketMQ 异步下单链路。
要求给出关键文件路径、调用流程、幂等设计、失败重试和补偿点。
```

预期产出：

- 找到消息发送、消费者、订单应用服务、最终一致性文档或测试。
- 说明抢票成功后发布订单创建消息。
- 说明消费者幂等创建订单。
- 说明失败重试和库存释放补偿。

通过标准：

- 输出至少 3 个真实文件路径。
- 不只写概念，要结合 TicketRush 实现。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 通过 |
| 得分 | 2 |
| 耗时 | 未单独计时 |
| 工具调用次数 | 19（检索 RocketMQ/Order/幂等/补偿关键词，读取关键源码、配置、测试和工作区状态） |
| 人工介入次数 | 0 |
| 实际产出摘要 | 关键链路为 `RushTicketApplicationService` 库存预占成功后构造 `OrderCreateMessage`，通过 `OrderCreateMessagePublisher` 抽象交给 `RocketMqOrderCreateMessagePublisher` 使用 `StreamBridge` 发送到 `orderCreate-out-0`，消息 header 写入 `KEYS=idempotentKey` 和 `TAGS=order-create`。`application.yml` 将 `orderCreate-out-0` 与 `orderCreateConsumer-in-0` 绑定到 `ticketrush-order-create-topic`，消费者组为 `ticketrush-order-consumer-group`，并配置 `max-attempts=3`。`RocketMqOrderCreateConsumerConfig` 暴露 `orderCreateConsumer`，委托 `OrderApplicationService#createOrder` 创建 `PENDING` 订单。消费幂等基于 `TicketOrderRepository#existsByIdempotentKey`，数据库层还有 `uk_ticket_order_idempotent_key` 唯一键兜底。补偿路径包括消息发送失败时入口释放预占库存，以及 `OrderTimeoutCloseJob` 批量关闭过期 `PENDING` 订单后释放库存。测试证据为 `RocketMqOrderCreateStreamIntegrationTest` 覆盖发布、消费落单、重复消息幂等和 RocketMQ headers。 |
| 失败原因 | 无 |

### E03 解释 Redis Lua 防超卖方案

输入：

```text
解释 TicketRush 的 Redis Lua 防超卖方案。
要求给出核心类、Lua 脚本位置、库存结构、幂等 Key、失败场景和边界处理。
```

预期产出：

- 找到 Redis Lua 相关适配器和脚本。
- 说明库存检查、原子扣减、锁定库存、版本递增或幂等处理。
- 说明库存不足、重复请求、未预热库存等错误映射。

通过标准：

- 输出文件路径和关键业务规则。
- 能和 Redis Lock、MySQL 乐观锁方案区分。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 通过 |
| 得分 | 2 |
| 耗时 | 未单独计时 |
| 工具调用次数 | 10（读取 Redis Lua 仓储、Lua 脚本、Key/字段定义、Redis Lock/MySQL 策略和相关测试） |
| 人工介入次数 | 0 |
| 实际产出摘要 | 核心方案位于 `RedisLuaTicketInventoryRepository` 和 `src/main/resources/lua/reserve_stock.lua`。库存结构是 `ticketrush:inventory:{skuId}` Hash，字段包括 `total`、`available`、`locked`、`sold`、`version`；幂等 Key 为 `ticketrush:idempotent:{idempotentKey}`。Lua 脚本在 Redis 单线程内原子完成幂等检查、库存 Key 存在性检查、可售库存校验、`available` 扣减、`locked` 增加、`version` 递增和幂等 Key 写入。返回码 `1/0/-1/-2` 分别映射为成功、可售库存不足、重复请求、库存不存在或未预热。与 Redis Lock 方案相比，Lua 方案不需要 `SET NX` 票档锁和解锁脚本，网络往返更少；与 MySQL 乐观锁相比，Lua 方案不依赖数据库 `version` 条件更新，热点票档写冲突压力更低。测试证据为 `RedisInventoryIntegrationTest` 覆盖 Lua 原子预占、重复请求不重复扣减，以及 Redis Lock 对比路径；应用层 `RushTicketApplicationServiceTest` 覆盖重复请求到业务异常、消息发送失败释放预占库存等边界。 |
| 失败原因 | 无 |

### E04 判断 Docker Compose 启动前置条件

输入：

```text
判断 TicketRush 当前 Docker Compose 全链路启动的前置条件。
要求说明是否需要先构建 JAR、app 服务如何挂载 JAR、健康检查怎么看、哪些容器是核心依赖。
```

预期产出：

- 说明当前 compose 的 app 服务依赖本地 `target/ticketrush-0.0.1-SNAPSHOT.jar`。
- 说明启动前需要 `mvn clean package` 或 `mvn clean verify`。
- 说明 app、mysql、redis、rocketmq、prometheus、grafana 等状态检查方式。

通过标准：

- 能指出本地 JAR 挂载这一关键约束。
- 不误称 Dockerfile 会在 compose 中自动构建 app 镜像。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 通过 |
| 得分 | 2 |
| 耗时 | 未单独计时 |
| 工具调用次数 | 4（读取 `docker-compose.yml`、`Dockerfile`、项目文档验证记录和 TicketRush 工作区状态） |
| 人工介入次数 | 0 |
| 实际产出摘要 | 当前 `docker-compose.yml` 的 `app` 服务使用 `image: eclipse-temurin:21-jre`，不是 `build:`，并通过 `./target/ticketrush-0.0.1-SNAPSHOT.jar:/app/ticketrush.jar:ro` 挂载本地 JAR，入口命令是 `java $JAVA_OPTS -jar /app/ticketrush.jar`。因此启动前需要先在本机执行 `mvn clean package`、`mvn clean verify` 或确保 `target/ticketrush-0.0.1-SNAPSHOT.jar` 已存在。核心依赖是 `mysql`、`redis`、`nacos`、`rocketmq-namesrv`、`rocketmq-broker`，其中 app 的 `depends_on` 等待 MySQL/Redis healthy、Nacos/Broker started；Prometheus/Grafana 是观测依赖，不是抢票主链路启动前置。健康验证可看 `docker compose ps`，访问 `/actuator/health`、`/api/system/health`，以及 `/actuator/prometheus`。当前 compose 没有 app 自身 healthcheck，HANDOFF 记录的验证方式是 `docker compose up -d` 10/10 容器运行、接口 health UP、库存预热和抢票成功。 |
| 失败原因 | 无 |

### E05 生成 k6 最小压测步骤

输入：

```text
为 TicketRush 生成一轮最小 k6 压测步骤。
要求包含前置构建、Docker 启动、库存预热、压测命令、核心观察指标和结果记录模板。
```

预期产出：

- 找到 `scripts/k6` 下的压测脚本。
- 给出库存预热接口和抢票接口。
- 给出 Prometheus/Grafana 或日志观察指标。
- 给出结果记录模板。

通过标准：

- 步骤能被人工按顺序执行。
- 不虚构不存在的脚本路径。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 未执行 |
| 得分 | - |
| 耗时 | - |
| 工具调用次数 | - |
| 人工介入次数 | - |
| 实际产出摘要 | - |
| 失败原因 | - |

### E06 找出当前未完成任务

输入：

```text
从 TicketRush 的 SPEC.md 和 HANDOFF.md 汇总当前未完成任务。
要求按优先级排序，并说明每项为什么重要。
```

预期产出：

- 找出 k6 真实压测、Seata 示例、稳定性压测记录等未完成项。
- 区分“求职必须做”和“可选增强”。
- 给出下一步只做一个任务。

通过标准：

- 输出基于文档证据。
- 不把已完成任务重复列为待办。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 未执行 |
| 得分 | - |
| 耗时 | - |
| 工具调用次数 | - |
| 人工介入次数 | - |
| 实际产出摘要 | - |
| 失败原因 | - |

### E07 评审一次小改动风险

输入：

```text
假设 TicketRush 准备修改 application-docker.yml 中的健康检查配置。
请以 code review 方式评审风险、影响范围和验证方式，不要直接改代码。
```

预期产出：

- 说明影响 Docker profile。
- 说明不应影响 test/local profile。
- 建议验证 `mvn clean verify`、`git diff --check`、`/actuator/health`、`/api/system/health`。

通过标准：

- 评审先列风险和验证。
- 不越权执行修改。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 未执行 |
| 得分 | - |
| 耗时 | - |
| 工具调用次数 | - |
| 人工介入次数 | - |
| 实际产出摘要 | - |
| 失败原因 | - |

### E08 补充一条文档验证记录

输入：

```text
根据以下验证结果，补充 TicketRush HANDOFF.md 的已验证部分：
mvn clean verify 通过，35 tests；docker compose up -d 10/10 容器启动；/actuator/health UP；/api/system/health UP。
只改 HANDOFF.md，不提交。
```

预期产出：

- 只修改 `HANDOFF.md`。
- 准确记录验证结果。
- 最后输出 git status。

通过标准：

- 不改业务代码。
- 不自动提交。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 未执行 |
| 得分 | - |
| 耗时 | - |
| 工具调用次数 | - |
| 人工介入次数 | - |
| 实际产出摘要 | - |
| 失败原因 | - |

### E09 判断运行数据是否应提交

输入：

```text
TicketRush 工作区出现 docker/rocketmq/store/ 未跟踪目录。
判断它是否应该提交，并给出理由、风险和处理建议。
不要删除文件。
```

预期产出：

- 判断该目录大概率是 RocketMQ 运行数据。
- 建议不提交，加入 `.gitignore`。
- 提醒不要直接删除，先确认容器是否依赖。

通过标准：

- 不执行删除。
- 能说明运行数据进入 Git 的风险。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 未执行 |
| 得分 | - |
| 耗时 | - |
| 工具调用次数 | - |
| 人工介入次数 | - |
| 实际产出摘要 | - |
| 失败原因 | - |

### E10 生成面试讲法

输入：

```text
基于 TicketRush 项目生成一段 2 分钟面试讲法。
重点讲高并发抢票链路、Redis Lua 防超卖、RocketMQ 异步削峰、Sentinel 限流、Docker Compose 验证和它为什么能作为 SmartKB v2 的 Agent eval 样本。
```

预期产出：

- 有 2 分钟版回答。
- 有 3-5 个面试追问点。
- 能把 TicketRush 和 SmartKB v2 的 eval 关系讲清楚。

通过标准：

- 不夸大为生产系统。
- 能体现 Java 后端经验和 AI 工程化转型的连接。

执行记录：

| 字段 | 内容 |
| --- | --- |
| 状态 | 未执行 |
| 得分 | - |
| 耗时 | - |
| 工具调用次数 | - |
| 人工介入次数 | - |
| 实际产出摘要 | - |
| 失败原因 | - |

## 7. 汇总

| 指标 | 当前值 |
| --- | --- |
| Eval case 总数 | 10 |
| 已执行 | 4 |
| 通过 | 4 |
| 部分通过 | 0 |
| 失败 | 0 |
| 总分 | 8 / 20 |
| 平均分 | 2.00 |
| 总人工介入次数 | 0 |

## 8. 初步结论

E01-E04 已通过，说明当前接管提示词可以稳定产出项目目标、阶段、已完成/未完成、工作区状态、风险点和单一下一步，也能在真实 Java 项目中跨应用层、基础设施层、配置、数据库、测试和 Docker Compose 文件追踪核心业务链路。

当前判断：

- TicketRush 适合作为 SmartKB v2 第一批 eval 样本，项目复杂度足够覆盖 Java 后端接管场景。
- 首个 case 验证了“项目理解、交接文档提取、git 状态读取、风险判断和下一步收敛”。
- E02 验证了 RocketMQ 异步下单链路的上下文检索能力，能把消息发送、消费者绑定、订单幂等、失败重试、库存补偿和集成测试串起来。
- E03 验证了 Redis Lua 防超卖方案的实现级解释能力，能定位脚本、Key 结构、失败码映射，并和 Redis Lock、MySQL 乐观锁方案区分。
- E04 验证了 Docker Compose 配置判断能力，能识别当前 app 服务依赖本地 JAR 挂载，而不是 compose 自动构建镜像。
- E01 的下一步建议聚焦真实 k6 压测，符合 TicketRush 当前最缺真实数据报告的状态。
- 暂不评测自动大规模改代码，后续可以继续验证压测步骤生成、未完成任务汇总和风险评审能力。

## 9. 下一步

1. 执行 E05-E07，验证压测步骤生成、未完成任务汇总和风险评审能力。
2. 根据 E01-E04 结果固化项目接管与链路解释输出格式。
3. 形成第一版 `Project Intake` 后端接口设计。
