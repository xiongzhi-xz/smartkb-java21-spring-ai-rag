# SmartKB SPEC

## 当前目标

把 SmartKB 打磨成一个可本地运行、可演示、可解释的 Java 21 + Spring AI RAG 项目。当前优先级是先稳定完成演示闭环：

```text
Docker 启动 PostgreSQL/Redis -> Ollama 提供 Embedding -> IDEA 启动 Spring Boot
-> 上传测试文档 -> 普通对话流式回答 -> Advanced RAG 指定文档回答 -> 展开引用片段
```

## 已完成

- [x] PostgreSQL + pgvector 向量存储
- [x] Redis 基础设施
- [x] Ollama `nomic-embed-text` 本地 Embedding
- [x] Spring Boot `hybrid` 本地运行模式
- [x] 文档上传、解析、切片、向量入库
- [x] 文档列表、详情、删除、统计接口
- [x] 普通 RAG 问答
- [x] 多轮对话
- [x] 对话流式输出
- [x] 对话发送后等待反馈
- [x] Advanced RAG 查询改写
- [x] Advanced RAG 分阶段反馈（查询改写、检索、过滤、重排序、生成）
- [x] Advanced RAG 阶段耗时指标（改写、召回、过滤、重排序、生成、总耗时）
- [x] Advanced RAG 按文档过滤
- [x] Advanced RAG 引用片段展示
- [x] Advanced RAG 引用片段点击定位到文档详情 chunk
- [x] 已上传文档详情窗口可拖动调整宽度
- [x] Markdown/TXT 按 UTF-8 读取，避免中文乱码
- [x] Advanced RAG 双路召回、过滤下推、关键词重排序
- [x] Advanced RAG 锚点词重排序，优先命中查询改写、引用片段等核心章节
- [x] Advanced RAG Hybrid Search 基础版：向量召回 + 关键词召回融合
- [x] 前端文档删除入口与后端删除 SQL 修复
- [x] 改进与问题复盘文档 `docs/IMPROVEMENTS_AND_ISSUES.md`
- [x] 大文档演示语料 `test-docs/advanced-rag-demo.md`
- [x] Redis 会话记忆持久化（自研 RedisChatMemory）
- [x] Advanced RAG 接入 ChatMemory 体系（conversationId 统一管理）
- [x] 新建会话清空 ChatMemory（前端 + 后端 DELETE API）
- [x] 两种模式共享同一 conversationId（刷新/重启后可恢复会话）

## Redis 会话记忆 — 面试讲法

### 核心问题
InMemoryChatMemory 只存在 JVM 内存，服务重启即丢失，不支持分布式共享。

### 解决方案
自研 `RedisChatMemory` 实现 Spring AI 的 `ChatMemory` 接口（3 个方法：add/get/clear）：
- **Redis List 结构**：`smartkb:chat:{conversationId}` 存储会话消息序列
- **TTL 过期**：默认 24 小时，活跃会话自动续期
- **JSON 序列化**：`{"type":"USER","content":"xxx"}`，避免 Java 序列化的安全风险和版本耦合
- **容错降级**：Redis 不可用时自动降级为 InMemoryChatMemory

### 为什么不用 Spring AI 官方 RedisChatMemory？
Spring AI 1.0.0-M1 版本没有提供 Redis ChatMemory 实现。ChatMemory 接口只有 3 个方法，
自研非常轻量，反而更能体现对接口设计和存储选型的理解。

### Advanced RAG 的 ChatMemory 改造
- 旧方案：前端 `buildHistoryText()` 拼接最近 10 条消息为纯文本字符串传给后端
- 新方案：后端通过 `conversationId` 从 ChatMemory 读取历史，写入 ChatMemory
- 好处：前端刷新/服务重启后会话可恢复；两种模式共享 conversationId

### 面试 40 分钟怎么讲
1. 先讲问题：InMemoryChatMemory 只存在内存，重启丢失
2. 再讲方案：ChatMemory 接口设计（add/get/clear）→ Redis List + TTL → 降级兜底
3. 然后讲 Advanced RAG 的改造：前端拼文本 → 后端 ChatMemory 统一管理
4. 最后讲延伸：Redis 除了会话记忆还能做缓存、限流、分布式锁

## 当前已知状态

- 本地服务启动方式：Docker Desktop 启动 PostgreSQL/Redis，Ollama 本机运行，Spring Boot 在 IDEA 中启动。
- SmartKB 主页面：`http://localhost:8080`
- 不是 Vue/Vite 独立前端，`http://localhost:3000` 不是主页面。
- 当前自动化测试通过：`mvn test`，共 11 个测试。
- 如果数据库里有修复前上传的 `advanced-rag-demo.md`，内容可能仍是乱码，需要删除后重新上传。
- Redis 会话记忆 Key 前缀：`smartkb:chat:`，TTL 默认 24 小时，可通过 `smartkb.chat-memory.ttl-hours` 配置。

## Redis 会话记忆验证清单

- [ ] 重启 Spring Boot，确认日志显示 `初始化 ChatMemory (Redis 模式, TTL=24h)`
- [ ] conversation 模式提问后，`redis-cli keys smartkb:chat:*` 能看到会话 Key
- [ ] 刷新页面后同一 conversationId 追问，LLM 能记住之前的对话
- [ ] 重启 Spring Boot 后同一 conversationId 追问，LLM 能记住之前的对话
- [ ] Advanced 模式同样共享 conversationId，追问时查询改写能基于历史
- [ ] 点击"新会话"后，Redis 中对应 Key 被删除，下次提问是新会话

## 下一步开发

- [ ] 生产级可观测性指标和性能压测报告（OpenTelemetry + Prometheus + Grafana）
- [ ] Docker/K8s 部署方案

## 新对话接续方式

新对话先读：

```powershell
Get-Content -Raw SPEC.md
git log --oneline -5
```

然后优先从"Redis 会话记忆验证清单"继续。
