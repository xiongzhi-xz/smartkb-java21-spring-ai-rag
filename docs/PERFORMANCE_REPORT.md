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

## 待执行记录

| 日期 | Commit | 场景 | 数据规模 | 并发/次数 | 结果 | 报告 |
| --- | --- | --- | --- | --- | --- | --- |
| 待执行 | 待记录 | Milvus + OpenSearch retrieval smoke | 2 chunks | 单次回环 | 待运行 | `target/reports/retrieval-backends-smoke.md` |
