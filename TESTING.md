# SmartKB 测试指南

## 自动化测试

```bash
mvn test
git diff --check
```

默认测试覆盖：

- 文档解析与切片
- Embedding 批处理
- 向量存储封装
- Advanced RAG 编排
- BGE Reranker 调用与规则降级
- RAG 检索评测
- 低置信度拒答与答案质量 Judge 解析
- Redis ChatMemory 单元行为
- Controller Web 层
- 静态 RAG 工作台结构
- Docker/K3s 清单结构

## 集成测试

默认集成测试只运行自包含的 PostgreSQL Testcontainers 用例（企业文档生命周期）：

```bash
mvn -Pintegration-tests verify
```

需要本机 Docker 可用。Milvus/OpenSearch 检索 smoke 不属于默认 profile，它们需要已启动的 Compose 后端或故障注入参数，并由下方专用脚本显式执行。

Java 25 的 Surefire/Failsafe 测试 JVM 会自动加载 Spring Boot 管理的 Mockito agent，并启用未命名模块的 native access；不需要在命令行额外配置 agent。

该用例会对 Flyway V1/V2 迁移后的真实 PostgreSQL 验证重复上传幂等、重复消费状态保护、失败重试和稳定文档删除。运行前先确认：

```bash
docker version --format '{{.Server.Version}}'
```

在 Docker Desktop 29 的 Windows 环境中，Testcontainers 1.20.6 需要使用 Docker socket 容器运行，并显式设置 Docker API 版本：

```powershell
docker run --rm `
  -e TESTCONTAINERS_RYUK_DISABLED=true `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -v "${PWD}:/workspace" `
  -v "${env:USERPROFILE}\.m2:/root/.m2" `
  -v "/var/run/docker.sock:/var/run/docker.sock" `
  -w /workspace maven:3.9-eclipse-temurin-25 `
  mvn -B "-Dapi.version=1.40" -P integration-tests verify
```

Milvus + OpenSearch Compose 检索 smoke：

```powershell
./scripts/smoke/retrieval-backends.ps1
```

HTTP 并发基线（默认健康端点）：

```powershell
./scripts/performance/http-load.ps1 -Requests 200 -Concurrency 20 -WarmupRequests 20
```

脚本把吞吐、P50/P95/P99 和错误率写入 `target/reports/http-load.md`。它只测量指定 HTTP GET 路径；要发布 RAG 性能结论，仍需固定文档/Chunk 规模、模型、查询集和运行环境。

该命令只启动 `milvus` 和 `opensearch`，运行 `RetrievalBackendsSmokeIT` 的一次确定性写入、检索和删除回环，并生成 `target/reports/retrieval-backends-smoke.md`。报告只记录实际观察到的每步耗时和错误，不推导 QPS、P95/P99 或容量结论。默认保留服务运行；需要停止时使用：

```powershell
./scripts/smoke/retrieval-backends.ps1 -StopServices
```

如果本机 `9200` 已被其他 Elasticsearch/OpenSearch 实例占用，可显式指定宿主机端口：

```powershell
./scripts/smoke/retrieval-backends.ps1 -OpenSearchPort 19200
```

真实后端故障降级 smoke：

```powershell
./scripts/smoke/retrieval-degradation.ps1 -OpenSearchPort 19200
```

该命令使用真实 Compose 中的 Milvus 和 OpenSearch，依次执行：

- `seed`：向两个后端写入确定性测试数据。
- `keyword-only`：停止 Milvus，预期 OpenSearch 继续检索并报告 `keyword-only`。
- `dense-only`：停止 OpenSearch，预期 Milvus 继续检索并报告 `dense-only`。
- `unavailable`：停止两个后端，预期返回 `RETRIEVAL_UNAVAILABLE`。
- `cleanup`：恢复后端并删除本次创建的临时 collection/index。

脚本会把每个场景的实际耗时、尝试次数和错误写入 `target/reports/retrieval-degradation-smoke.md`，并在结束时检查清理/恢复结果。`dense-only` 和 cleanup 最多有限重试，用于等待 Milvus 重启后的 collection 恢复；该 smoke 不是并发压测，不提供 QPS、P95/P99 或容量结论。

## 本地链路验证

### 1. 启动 PostgreSQL 和 Redis

```bash
docker compose -f docker-compose-minimal.yml up -d
```

### 2. 准备 Ollama

```bash
ollama pull nomic-embed-text
ollama list
```

### 3. 启动应用

使用 `hybrid` profile，并配置 Chat API 的 key、base URL 和模型名。

### 4. 验证文档链路

上传：

```text
test-docs/advanced-rag-demo.md
```

确认：

- 上传成功并返回 chunk 数量
- 文档详情可以查看 chunks
- PostgreSQL 中存在向量记录

### 5. 验证问答链路

普通模式：

- 首轮问题能够流式返回
- 同一 conversation ID 的追问能够读取历史
- 应用重启后 Redis 会话仍可恢复

Advanced 模式：

- 查询改写阶段可见
- 检索、过滤、重排和生成阶段可见
- 引用片段能够定位对应 chunk
- `rerank_done` 阶段中的 `reranker` 为 `bge-rule-hybrid`

### 6. 验证评测

调用：

```text
POST /api/rag/eval/run
```

确认报告包含：

- Recall@K
- Top1
- MRR
- 引用覆盖率
- 普通与 Advanced 检索对比

### 7. 验证监控

确认：

- `/actuator/health` 返回 UP
- `/actuator/prometheus` 包含 SmartKB 自定义指标
- Grafana Dashboard 可以展示 RAG 阶段耗时和请求计数

## Reranker A/B 对比

启用模型重排运行一次评测：

```text
SMARTKB_RERANKER_ENABLED=true
POST /api/rag/eval/run
```

验证低置信度拒答：在知识库只包含项目技术文档时，询问明显无关的问题。Advanced RAG 应返回 `refused=true`、`confidence` 和 `refusalReason`，并且不调用生成模型。

验证答案质量 Judge：

```http
POST /api/rag/eval/answer
Content-Type: application/json

{
  "question": "查询改写解决什么问题？",
  "answer": "查询改写将口语化问题转换为更适合检索的表达。",
  "contexts": ["查询改写会结合上下文，将问题改写为更适合检索的表达。"]
}
```

响应包含 `faithfulness`、`answerRelevance`、`contextRelevance`、`overallScore` 和各项理由。该结果属于 LLM-as-Judge，适合回归比较，不应视为客观真值。

关闭模型重排并重启应用，再运行相同评测：

```text
SMARTKB_RERANKER_ENABLED=false
POST /api/rag/eval/run
```

对比 Advanced Top1、MRR 和每个 case 的 `rerankerMode`。测试集、Top-K 和文档数据必须保持一致。

当前固定 8 用例的本地记录：

| 策略 | Top1 | MRR |
| --- | --- | --- |
| 规则重排 | 8/8 | 1.0 |
| 纯 BGE | 6/8 | 0.875 |
| BGE + 规则 RRF 融合 | 8/8 | 1.0 |

这组用例原本针对规则检索链路设计，因此只能用于回归和方案对比，不能作为通用 Reranker 榜单。

## 浏览器 smoke

```bash
node scripts/smoke/workbench-desktop-screenshots.mjs
```

用于检查上传、问答、Advanced RAG、引用定位和评测页面的主要交互。

## 常见问题

### 检索结果为空

- 检查文档是否成功入库。
- 检查 Ollama Embedding 模型和向量维度。
- 检查 pgvector 表中的记录数量。
- 降低相似度阈值进行排查，但不要把排查值直接作为最终配置。

### Redis 会话未恢复

- 确认使用相同 conversation ID。
- 检查 Redis key 是否存在以及 TTL。
- 确认当前没有降级到内存 ChatMemory。

### 虚拟线程日志显示 NO

入口线程可能仍是平台线程；应检查批处理任务内部线程是否为虚拟线程。

## 性能说明

不要引用未经复现的固定 QPS 或倍数。性能结论必须记录：

- Java 版本
- 硬件和容器资源
- 文档数量与大小
- 并发数
- 模型和网络条件
- P50、P95、P99 与错误率

## Deployment configuration acceptance

```powershell
docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose-minimal.yml config --quiet
npx --yes js-yaml k8s/k3s-demo.yaml
mvn -B -Dtest=K3sDemoManifestTest test
```

These commands validate Compose parsing, YAML parsing, and K3s manifest structure; they do not replace a full runtime check. See [docs/DEPLOYMENT_VERIFICATION.md](docs/DEPLOYMENT_VERIFICATION.md) for the 2026-07-23 results and blockers.
