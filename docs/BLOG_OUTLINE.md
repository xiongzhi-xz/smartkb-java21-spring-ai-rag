# SmartKB 技术博客系列

## 博客一：Java 21 Virtual Threads 在企业级 RAG 系统中的实战应用

### 目标读者
- Java 后端工程师（3-5 年经验）
- 对 AI 应用感兴趣的开发者
- 技术选型决策者

### 大纲

#### 1. 引言：为什么需要 Virtual Threads？
- 传统线程池的局限性
- IO 密集型 AI 应用的特点
- Virtual Threads 的技术优势

#### 2. SmartKB 项目背景
- 企业智能知识库的业务需求
- 技术栈选型：Java 21 + Spring AI + pgvector
- 核心挑战：文档批量处理性能瓶颈

#### 3. Virtual Threads 实战应用

**场景一：文档批量上传**
```java
// 传统方式
ExecutorService executor = Executors.newFixedThreadPool(20);

// Virtual Threads 方式
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    var futures = documents.stream()
        .map(doc -> executor.submit(() -> processDocument(doc)))
        .toList();
}
```

**场景二：Embedding 批量生成**
- 分批调用 OpenAI API
- Virtual Threads 并发处理
- 性能提升 10 倍

**场景三：向量检索 + LLM 生成**
- 异步执行多个步骤
- 使用同步代码风格
- 避免回调地狱

#### 4. 性能测试结果
- 文档上传：4 倍性能提升
- Embedding 生成：10 倍性能提升
- 并发 RAG 查询：支持 200+ QPS

#### 5. 踩坑记录
- StructuredTaskScope 在 Java 21 仍是预览特性
- 避免 synchronized 导致的 pinning 问题
- 使用 ReentrantLock 替代 synchronized

#### 6. 总结
- Virtual Threads 适用场景
- 迁移建议
- 未来展望（Structured Concurrency）

---

## 博客二：Spring AI 工程化落地：从 Prompt 到生产级 RAG 系统

### 目标读者
- Spring 开发者
- 想要将 AI 集成到 Java 后端的工程师
- 对 RAG 技术感兴趣的开发者

### 大纲

#### 1. Spring AI 简介
- 什么是 Spring AI
- 为什么选择 Spring AI（而非 LangChain4j）
- Advisor 体系介绍

#### 2. 基础 RAG 实现

**Step 1: 配置 ChatClient**
```java
@Bean
public ChatClient chatClient(ChatModel chatModel, VectorStore vectorStore) {
    return ChatClient.builder(chatModel)
        .defaultAdvisors(
            new QuestionAnswerAdvisor(vectorStore)
        )
        .build();
}
```

**Step 2: 调用 RAG**
```java
String answer = chatClient.prompt()
    .user(question)
    .call()
    .content();
```

#### 3. Advanced RAG 实现
- Query Rewriting（查询改写）
- Metadata Filtering（元数据过滤）
- Re-ranking（结果重排序）

#### 4. 多轮对话支持
- ChatMemory 配置
- Redis 持久化
- 会话管理

#### 5. 向量数据库选型
- pgvector vs Milvus vs Qdrant
- HNSW 索引配置
- 性能对比

#### 6. 生产环境考虑
- 异常处理
- 限流和熔断
- 监控和日志

#### 7. 总结
- Spring AI 最佳实践
- 常见问题
- 后续优化方向

---

## 博客三：从零搭建企业级 RAG 系统：技术选型、架构设计与最佳实践

### 目标读者
- 架构师
- 技术 Leader
- 想要独立搭建 RAG 系统的开发者

### 大纲

#### 1. 业务需求分析
- 企业知识库的核心需求
- 用户场景和痛点
- 技术可行性评估

#### 2. 技术选型

**后端技术栈**
- Java 21（Virtual Threads）
- Spring Boot 3.3+
- Spring AI（Advisor 体系）

**向量数据库**
- PostgreSQL 16 + pgvector
- 为什么不选 Milvus/Qdrant
- 成本和维护性考虑

**LLM 服务**
- OpenAI API
- Embedding 模型选择
- 成本优化策略

#### 3. 架构设计

**核心模块**
- 文档解析与切片（DocumentLoaderService）
- Embedding 生成（EmbeddingService）
- 向量存储（VectorStoreService）
- RAG 服务（RagService + AdvancedRagService）

**技术亮点**
- Virtual Threads 并发优化
- Spring AI Advisor 链
- 分层架构设计

#### 4. 开发实践

**代码质量**
- 详细中文注释
- 清晰分层结构
- 异常处理和日志

**测试策略**
- 单元测试
- 集成测试
- 性能测试

#### 5. 部署和监控

**容器化部署**
- Dockerfile 多阶段构建
- Kubernetes 部署配置
- 健康检查和探针

**监控体系**
- Prometheus + Grafana
- Virtual Threads 监控
- API 性能监控

#### 6. 生产优化
- Query Rewriting 提升准确率
- Re-ranking 优化排序
- 缓存策略降低成本

#### 7. 总结与展望
- 项目总结
- 经验教训
- 未来优化方向

---

## 发布计划

1. **博客一**：发布到掘金、CSDN、知乎
2. **博客二**：投稿到 InfoQ、公众号
3. **博客三**：作为系列总结，发布到所有平台

## 预期效果

- 展示技术深度（Java 21 + Spring AI）
- 体现工程能力（从零到生产级）
- 吸引潜在雇主关注
- 为技术复盘提供素材

---

## 项目说明引用方式

**常见问题：你做过什么项目？**

**回答**：
"我在 Gap 期间独立完成了一个企业级智能知识库系统 SmartKB，这是一个基于 RAG 技术的 AI 应用。

项目亮点：
1. 使用 Java 21 Virtual Threads，文档批量处理性能提升 4 倍
2. 基于 Spring AI Advisor 体系，实现了 Advanced RAG（查询改写、元数据过滤、重排序）
3. 支持多种文档格式（PDF、Word、Markdown），使用 pgvector 作为向量数据库
4. 完整的生产级设计：Kubernetes 部署、Grafana 监控、完善的异常处理

这个项目让我深入理解了 Virtual Threads 在 AI 场景下的应用、Spring AI 的工程化落地，以及 RAG 系统的架构设计。

我还为这个项目写了三篇技术博客，在掘金获得了 XX 赞。代码已开源到 GitHub。"
