# HANDOFF - SmartKB

## 当前目标

把 SmartKB 收口为可本地运行、可演示、可面试讲解的 Java 21 + Spring AI RAG 项目，并作为后续 AI 工程化转型主项目继续升级。

新的求职叙事方向：

- 第一阶段：企业智能知识库，展示 Spring AI、Advanced RAG、pgvector、Redis ChatMemory、Docker Compose、Prometheus/Grafana。
- 第二阶段：升级为面向 Java 存量项目的 Agent 工程平台，增加项目接管、任务状态机、长期记忆、上下文检索和 eval 能力。

## 当前阶段

Docker Compose 全链路启动已验证通过，收口阶段基本完成。待验证 Redis ChatMemory 完整清单和 K3s 部署。

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

## 正在做

当前无进行中的改动，所有验证已通过。

## 下一步

1. 验证 Redis ChatMemory 完整清单（见 SPEC.md 的 6 项验证点）。
2. 考虑提交当前改动（Dockerfile + `.mvn/` + HANDOFF.md）。
3. K3s 部署方案设计。
4. 进一步优化和面试准备材料。

## 已修改文件

本轮改动：

- `Dockerfile` — 多阶段构建 + exec 形式 ENTRYPOINT
- `.mvn/settings.xml` — 阿里云镜像配置，`spring-milestones` 不走阿里云镜像（阿里云未同步 Spring AI milestone 包）
- `.mvn/maven.config` — `--batch-mode`
- `HANDOFF.md` — 交接文档更新

安全性检查：
- `.mvn/settings.xml` 无 API key、token、私有路径或账号信息。
- `.mvn/maven.config` 仅含 `--batch-mode`，无敏感信息。

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
