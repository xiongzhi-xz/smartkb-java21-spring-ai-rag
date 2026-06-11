# SmartKB - 企业智能知识库系统

> 基于 Java 21 + Spring AI + PostgreSQL pgvector 的生产级 RAG 系统

## 技术栈

- **Java 21** - Virtual Threads + Structured Concurrency
- **Spring Boot 3.3.1** - 企业级框架
- **Spring AI 1.0.0-M1** - Advisor 体系（RAG 核心）
- **PostgreSQL 16 + pgvector** - 向量数据库
- **Redis 7** - 会话记忆 + 缓存
- **OpenTelemetry + Prometheus + Grafana** - 可观测性

## 快速开始

### 1. 启动基础设施

```bash
docker-compose up -d
```

### 2. 配置环境变量

```bash
export OPENAI_API_KEY=your-api-key-here
```

### 3. 运行应用

```bash
mvn spring-boot:run
```

### 4. 访问服务

- 应用: http://localhost:8080
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin123)

## 项目结构

```
src/main/java/com/smartkb/
├── controller/      # REST API
├── service/         # 业务逻辑
├── domain/          # 领域模型
├── infrastructure/  # 基础设施
└── config/          # 配置类
```

## 开发计划

- [x] 基础设施配置
- [ ] 文档解析服务
- [ ] Embedding 生成
- [ ] 向量存储
- [ ] RAG 检索
- [ ] Agent 对话

## License

MIT
