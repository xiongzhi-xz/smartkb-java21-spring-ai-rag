# SmartKB 性能与检索验证记录

## 结论边界

当前仓库尚未完成可复现的并发压测，因此不提供固定 QPS、P95/P99、性能倍数或容量承诺。

已提供的 Milvus + OpenSearch 检索 smoke 只回答以下问题：

- 两个 Compose 检索后端是否健康可达。
- 当前适配器能否完成一次确定性的写入、带知识库过滤的检索和删除。
- 本次运行中每个操作实际观察到的耗时与错误是什么。

单次 smoke 耗时不能代表稳定延迟分位数，也不能外推吞吐量或生产容量。

## 可复现检索 smoke

在 Windows PowerShell 和项目根目录执行：

```powershell
./scripts/smoke/retrieval-backends.ps1
```

命令会：

1. 使用 `docker compose up -d --wait milvus opensearch` 启动并等待真实后端健康。
2. 运行 `RetrievalBackendsSmokeIT`。
3. 为本次运行创建隔离的 Milvus collection 和 OpenSearch index。
4. 对相同 chunk 执行写入、知识库过滤检索和按文档删除。
5. 尽力清理本次创建的临时索引。
6. 将运行记录写入 `target/reports/retrieval-backends-smoke.md`。

默认不会停止已启动的服务。需要在验证后停止这两个服务时执行：

```powershell
./scripts/smoke/retrieval-backends.ps1 -StopServices
```

如果宿主机 `9200` 端口已被占用，可改用其他宿主机端口：

```powershell
./scripts/smoke/retrieval-backends.ps1 -OpenSearchPort 19200
```

## 故障降级 smoke

在真实 Compose 后端上执行：

```powershell
./scripts/smoke/retrieval-degradation.ps1 -OpenSearchPort 19200
```

本次运行报告位于 `target/reports/retrieval-degradation-smoke.md`，结果为 `PASS`：

| 场景 | 依赖状态 | 预期结果 | Attempts | Status |
| --- | --- | --- | ---: | --- |
| `seed` | Milvus + OpenSearch | 确定性数据写入两个后端 | 1 | PASS |
| `keyword-only` | Milvus 停止 | OpenSearch 检索，模式为 `keyword-only` | 1 | PASS |
| `dense-only` | OpenSearch 停止 | Milvus 检索，模式为 `dense-only` | 2 | PASS |
| `unavailable` | 两端停止 | 返回 `RETRIEVAL_UNAVAILABLE` | 1 | PASS |
| `cleanup` | 后端恢复 | 清理临时 collection/index | 2 | PASS |

`dense-only` 和 cleanup 的第二次尝试是后端恢复后的有限重试，不是性能采样。该故障注入 smoke 证明降级分支和恢复清理可复现，但不提供固定 QPS、P50/P95/P99、吞吐量或容量承诺。

## 报告字段

本地生成报告包含：

- 生成时间。
- Milvus/OpenSearch 端点和临时索引名。
- 后端、操作、实际观察耗时、状态和错误。
- Compose 服务快照。
- 明确的 `PASS` 或 `FAILED` 结果。

报告位于 `target/`，属于本地验证产物，不提交到 Git。若用于评审或发布，应同时记录主机配置、Docker 资源限制和完整命令。

## 后续正式压测要求

正式性能报告至少要固定并记录：

- Git commit、Java/Maven/Docker 版本。
- CPU、内存、磁盘、操作系统和容器资源限制。
- 文档数量、chunk 数量、向量维度和索引规模。
- Ollama Embedding、ChatModel、网络条件及是否预热。
- 查询集、并发数、持续时间和升压策略。
- P50、P95、P99、吞吐量、错误率和失败分类。
- JVM、Milvus、OpenSearch、PostgreSQL 的资源观测。

在这些条件未落盘并可复现前，项目文档不引用固定 QPS、性能提升倍数或“支持数千并发”等结论。

## HTTP 并发基线工具

`scripts/performance/http-load.ps1` 使用 PowerShell runspace 并发执行 GET 请求，不新增压测依赖。默认目标是本地健康端点；完成完整运行环境准备后，可显式替换为已定义、无副作用的读接口。

```powershell
./scripts/performance/http-load.ps1 `
  -Url http://localhost:8080/actuator/health `
  -Requests 200 `
  -Concurrency 20 `
  -WarmupRequests 20 `
  -Scenario "actuator-health-baseline"
```

脚本将测量参数、成功/失败数、错误率、总耗时、吞吐量和 P50/P95/P99 写入 `target/reports/http-load.md`。任一请求失败时，报告仍会保留，但脚本以非零状态退出，不能将该次运行作为成功基线。

### 2026-07-24 isolated health baseline

使用隔离 Compose PostgreSQL/Redis、独立 SmartKB 进程和 `http://localhost:28080/actuator/health` 运行。100 次请求、10 并发、10 次预热全部成功；观测吞吐为 262.32 req/s，P50/P95/P99 分别为 23.82/41.86/50.70 ms。环境为 Windows 10.0.26200.0、20 个逻辑处理器和 PowerShell 5.1；详细本地报告位于 `target/reports/http-load-baseline.md`。

该结果只用于验证压测工具与本地 HTTP 健康路径，未包含文档数据、Ollama Embedding、ChatModel、MinIO、RabbitMQ、Milvus 或 OpenSearch，不得用作 RAG 吞吐或生产容量结论。

## 待执行记录

| 日期 | Commit | 场景 | 数据规模 | 并发/次数 | 结果 | 报告 |
| --- | --- | --- | --- | --- | --- | --- |
| 2026-07-23 | 本次 Phase 5c 提交 | Milvus/OpenSearch retrieval degradation smoke | 5 scenarios | 单次故障注入回环 | PASS | `target/reports/retrieval-degradation-smoke.md` |
| 2026-07-23 | e262b38 | Milvus + OpenSearch retrieval smoke | 2 chunks | 单次回环 | 已完成 | `target/reports/retrieval-backends-smoke.md` |
