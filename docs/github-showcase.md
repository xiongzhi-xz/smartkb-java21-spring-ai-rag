# SmartKB GitHub 展示说明

这份说明用于技术读者快速判断 SmartKB 的展示重点。它不是新功能清单，也不替代 README。

## 快速看点

- Java 21 + Spring Boot + Spring AI 的 Advanced RAG 工程闭环。
- PostgreSQL + pgvector 做向量存储，Ollama 提供本地 Embedding。
- Redis ChatMemory 支持多轮会话持久化、TTL 和服务重启后的上下文恢复。
- 工作台从 RAG 扩展到 Agent 工程能力：项目接管、任务状态、记忆分层、代码上下文和 Eval。
- Docker Compose、Prometheus/Grafana、K3s demo manifest 和测试记录都已落地。

## 推荐浏览顺序

1. [README.md](../README.md)：先看项目定位、架构图、启动方式、验证状态。
2. [DEMO.md](../DEMO.md)：按 5 分钟路径走完整演示。
3. [docs/demo-runbook.md](demo-runbook.md)：看设计说明、设计取舍和替代路径。
4. [SPEC.md](../SPEC.md)：看已完成范围、验收记录和不继续扩展的边界。
5. [docs/AGENT_PLATFORM_SPEC.md](AGENT_PLATFORM_SPEC.md)：看 SmartKB v2 Agent 平台设计。

## 本地演示入口

Docker Compose 模式：

```text
http://localhost:8082
```

Hybrid 本地开发模式：

```text
http://localhost:8080
```

推荐演示文档：

```text
test-docs/advanced-rag-demo.md
```

推荐演示问题：

```text
查询改写在 Advanced RAG 中解决什么问题？
为什么引用片段能提升 RAG 系统可信度？
```

## 已验证证据

- `mvn test`：106 tests passed。
- Docker Compose 运行态 health 已验证为 `UP`。
- Redis ChatMemory live checklist：6/6 通过。
- 工作台 browser smoke 覆盖桌面端和 390px 移动视口。
- Project Intake、AgentTask、Memory、Code Context、Eval 的摘要指标和移动端交互 smoke 已覆盖。
- K3s demo manifest 已在一次性 K3d 集群验证 Pod、PVC、health 和 Eval report API。

## 项目摘要

```text
SmartKB：基于 Java 21 + Spring AI 的企业知识库与 Agent 工程平台，完成文档上传、pgvector 检索、Advanced RAG、Redis 会话记忆、流式问答、Prometheus/Grafana 监控，并扩展项目接管、任务状态、记忆分层、代码上下文和 Eval 评测工作台。
```

## 边界说明

- 这是可本地运行、可演示、可解释的工程项目，不声称已经生产商用。
- K3s manifest 是本地 demo 形态，生产级 HA、TLS、镜像仓库、监控和托管 Secret 不在当前范围。
- Agent 平台聚焦可验证的项目接管和工程辅助，不包装成完整多 Agent OS。
- 仓库不应提交真实 API key、token、cookie、私钥或 `.env` 内容。