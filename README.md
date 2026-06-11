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
```bash
# 1. 启动基础设施
docker compose up -d

# 2. 启动应用
./mvnw spring-boot:run

# 3. 访问地址
应用地址: http://localhost:8080
Grafana: http://localhost:3000 (admin/admin123)
```

### 后续开发计划
- [x] 项目初始化 + Virtual Threads 配置 + CLAUDE.md 规则
- [ ] 文档智能解析与切片服务
- [ ] Embedding 与向量存储
- [ ] Advanced RAG（多Advisor组合）
- [ ] Tool-Calling Agent 与多轮对话
- [ ] Kubernetes 部署 + 完整监控面板
- [ ] 性能压测报告（Virtual Threads收益数据）
- [ ] 3篇技术博客输出

### 学习收获与总结
通过本项目，我将理论知识转化为可运行的生产级系统，深入理解了 Virtual Threads 在AI场景下的正确使用方式、Spring AI Advisor 的设计思想，以及RAG系统的工程化落地路径。这些经验将帮助我快速适应2026年的企业技术要求。

---
