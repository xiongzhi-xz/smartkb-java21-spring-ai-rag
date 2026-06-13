# SmartKB - 企业智能知识库系统（Advanced RAG + Agent）

> **Gap期间核心技术项目** —— 基于2026年主流技术栈，将大模型能力真正工程化落地到企业级Java后端系统。

这是一个我在2025-2026年Gap期间独立设计和开发的**生产级预研项目**。目标是通过这个项目系统性掌握 Java 21 Virtual Threads、Spring AI 工程化最佳实践，并形成可落地、可讲解、可监控的完整解决方案。

### 项目背景
过去8年我主要从事企业级业务系统开发（景区票务大数据、AI语音分析、SRM系统等）。Gap期间我没有停止技术迭代，而是系统学习了2026年主流技术方向，重点突破 **Java 21 Virtual Threads** 在AI场景的应用和 **Spring AI Advisor** 体系在企业级RAG中的落地。本项目即为该阶段的核心成果。

### 技术栈（2026主流选型）
- **Java 21** — Virtual Threads + Structured Concurrency（核心亮点）
- **Spring Boot 3.3+** — 应用框架
- **Spring AI** — Advisor体系（QuestionAnswerAdvisor、RetrievalAugmentationAdvisor、VectorStoreChatMemoryAdvisor）
- **PostgreSQL 16 + pgvector** — 向量数据库
- **Redis 7** — 会话记忆、缓存、限流
- **Docker + Kubernetes (K3s)** — 容器化部署
- **OpenTelemetry + Prometheus + Grafana** — 全链路可观测性
- **Arthas** — 线上诊断（后续加入）

### 核心功能与亮点
- 支持PDF、Word、Markdown等多种文档智能解析与切片
- **Advanced RAG**：Query Rewriting + Metadata Filtering + Re-ranking
- 多轮对话记忆 + Tool-Calling Agent（搜索、总结、导出等工具）
- 使用 Virtual Threads 大幅提升文档批量Embedding和检索并发性能
- 完整可观测性监控仪表盘（Grafana）
- 生产级考虑：异常处理、日志、单元测试友好、避免 Virtual Threads pinning 问题

### 项目结构
```
src/main/java/com/smartkb/
├── controller/          # API层
├── service/             # 业务服务（RAG Service、Agent Service）
├── domain/              # 领域模型
├── infrastructure/      # 向量存储、外部客户端
├── config/              # Spring AI Advisor配置、Virtual Threads配置
└── util/                # 文档解析、Prompt工具
```

### 快速启动
本地开发推荐使用 `hybrid` 模式：Docker Desktop 启动 PostgreSQL/Redis，Ollama 提供本地 Embedding，IDEA 启动 Spring Boot。

完整步骤见 [STARTUP.md](STARTUP.md)。

```bash
docker compose -f docker-compose-minimal.yml up -d
ollama pull nomic-embed-text
```

IDEA 运行配置：

- Active profiles: `hybrid`
- Environment variables: `TRANSIT_API_KEY=你的Key;TRANSIT_BASE_URL=https://fufu.iqach.top`
- 应用地址: http://localhost:8080

### 后续开发计划
- [x] 项目初始化 + Virtual Threads 配置 + CLAUDE.md 规则
- [x] 文档智能解析与切片服务
- [x] Embedding 与向量存储
- [x] 核心 RAG 服务（单轮/多轮问答）
- [x] REST API 接口
- [x] Advanced RAG（Query Rewriting、Metadata Filtering、Re-ranking）
- [x] 生产级功能（文档管理、异常处理）
- [x] Web 前端界面
- [x] 单元测试（核心服务）
- [x] Kubernetes 部署 + Grafana 监控面板
- [x] 性能测试报告模板
- [x] 技术博客大纲

### 项目文档
- [TESTING.md](TESTING.md) - 完整测试指南
- [PERFORMANCE_REPORT.md](docs/PERFORMANCE_REPORT.md) - 性能测试报告
- [BLOG_OUTLINE.md](docs/BLOG_OUTLINE.md) - 技术博客系列大纲
- [k8s/README.md](k8s/README.md) - Kubernetes 部署指南

## API 文档

### 文档管理
- `POST /api/documents/upload` - 上传文档
- `GET /api/documents` - 查询文档列表
- `GET /api/documents/{fileName}` - 查询文档详情
- `DELETE /api/documents/{fileName}` - 删除文档
- `GET /api/documents/stats` - 文档统计信息

### 问答服务
- `POST /api/chat` - 基础 RAG 问答
- `POST /api/chat/advanced` - Advanced RAG 问答（查询改写+过滤）
- `POST /api/chat/conversation` - 多轮对话
- `POST /api/test/rag` - 测试接口（带调试信息）

### 详细测试步骤
参考 [TESTING.md](TESTING.md) 获取完整测试指南。

### 学习收获与总结
通过本项目，我将理论知识转化为可运行的生产级系统，深入理解了 Virtual Threads 在AI场景下的正确使用方式、Spring AI Advisor 的设计思想，以及RAG系统的工程化落地路径。这些经验将帮助我快速适应2026年的企业技术要求。

---
