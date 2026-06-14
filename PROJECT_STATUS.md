# SmartKB 项目当前进度总结

## 重要复盘文档

- [docs/IMPROVEMENTS_AND_ISSUES.md](docs/IMPROVEMENTS_AND_ISSUES.md)：记录本阶段关键改进与工程复盘，包括流式输出、Advanced RAG 检索命中优化、数据摄取可靠性、引用片段可解释性、RAG 回归评估和混合模型架构取舍。

## 2026-06-13 收尾记录

### 今日新增修复
- ✅ `fix: 修复文本文件编码解析`：Markdown/TXT 直接按 UTF-8 读取，避免中文文档上传后变成乱码。
- ✅ `fix: 提升 advanced rag 检索召回`：Advanced RAG 现在同时召回原始问题和改写问题，支持 metadata 过滤下推，并按关键词相关度重排序。
- ✅ `feat: 增加 advanced rag hybrid search`：Advanced RAG 现在融合向量召回和关键词召回，减少核心章节被泛相关片段挤掉的概率。

### 明天继续前必须做
- [ ] 在 IDEA 中重启 Spring Boot，让最新代码生效。
- [ ] 删除修复前上传过的旧 `advanced-rag-demo.md`。
- [ ] 重新上传 `test-docs/advanced-rag-demo.md`。
- [ ] 打开文档详情，确认中文正常且不再乱码。
- [ ] Advanced 模式选择 `advanced-rag-demo.md` 后验证推荐问题：
  - `查询改写在 Advanced RAG 中解决什么问题？`
  - `为什么引用片段能提升 RAG 系统可信度？`

### 当前判断
- 如果页面仍显示 `æ¥è¯¢æ¹å` 这类文字，说明数据库里还是旧乱码数据，不是前端显示问题。
- 代码修复不会自动修复已入库的旧向量内容，必须删除旧文档后重新上传。
- 当前下一阶段建议先在页面回归验证 Hybrid Search，其次做引用片段点击跳转到对应 chunk。

## ✅ 已完成功能

### 1. 基础设施
- ✅ PostgreSQL 16 + pgvector 向量数据库（Docker 运行中）
- ✅ Redis 7（Docker 运行中）
- ✅ 数据库表已创建（vector_store 表 + HNSW 索引）
- ✅ Ollama 本地 Embedding 模型（nomic-embed-text，274MB）

### 2. 后端核心功能
- ✅ 文档上传接口（`POST /api/documents/upload`）
- ✅ 文档解析服务（支持 PDF、Word、Markdown、TXT）
- ✅ Embedding 生成服务（使用 Ollama nomic-embed-text）
- ✅ 向量存储服务（PostgreSQL + pgvector）
- ✅ RAG 问答接口（`POST /api/chat`）
- ✅ 多轮对话接口（`POST /api/chat/conversation`）
- ✅ Advanced RAG 接口（`POST /api/chat/advanced`，查询改写 + 元数据过滤 + 重排序）
- ✅ Advanced RAG 检索召回优化（原始问题 + 改写问题双路召回，metadata 过滤下推）
- ✅ Advanced RAG Hybrid Search（向量召回 + 关键词召回融合）
- ✅ 文档管理接口（列表、详情、删除、统计）
- ✅ Virtual Threads 配置（全局启用）
- ✅ Spring AI Advisor 体系（QuestionAnswerAdvisor）

### 3. 前端界面
- ✅ 响应式 UI（文档上传 + 对话界面）
- ✅ 上传进度提示（加载 → 成功 → 错误）
- ✅ 文档列表显示（实时更新）
- ✅ 已上传文档详情查看
- ✅ 文档详情窗口宽度调整
- ✅ 对话界面（问答交互）
- ✅ 对话模式流式输出（SSE 增量显示）
- ✅ 前端会话 ID 持久化（支持连续追问和新会话）
- ✅ Advanced RAG 模式切换与按文档过滤
- ✅ Advanced RAG 改写查询、命中片段数、来源文档与引用片段展示

### 4. 配置与部署
- ✅ 混合模式配置（Chat 用中转站 + Embedding 用本地）
- ✅ Docker Compose 简化版（PostgreSQL + Redis）
- ✅ 自定义 OpenAI 客户端配置（绕过 auto-configuration）

---

## ✅ 当前验证结果

### 对话与 RAG 链路已调通
- **服务健康**：`/actuator/health` 返回 `UP`
- **普通问答**：`POST /api/chat` 可正常返回答案
- **知识库问答**：`POST /api/test/rag` 可检索到 `virtual-threads-guide.md` 并生成答案
- **多轮对话代码链路**：`POST /api/chat/conversation` 已接入 ChatMemory Advisor，前端会复用 conversationId
- **流式输出代码链路**：`POST /api/chat/conversation/stream` 使用 SSE 推送增量 token，前端实时追加到回答气泡
- **Advanced RAG 代码链路**：查询改写、文档过滤、重排序、引用片段返回已接入，生成阶段直接调用 ChatModel，避免二次触发普通 RAG Advisor
- **Hybrid Search 代码链路**：Advanced RAG 候选集现在融合向量召回和关键词召回，关键词召回复用文档过滤条件
- **文本编码链路**：Markdown/TXT 上传解析已固定 UTF-8，重新上传后文档详情和引用片段应显示正常中文
- **Virtual Threads**：Controller 请求线程已确认为虚拟线程
- **自动化测试**：`mvn test` 通过（9 tests, 0 failures）

### 待关注
- 中转站模型响应耗时有波动，长答案可能超过 30 秒
- 已将 API Key 改为环境变量读取；此前暴露过的 Key 建议在中转站后台轮换

---

## 📋 后续开发计划

### 阶段 1：完成基础 RAG 功能（优先）
- [x] 修复对话功能（中转站 API 兼容性）
- [x] 测试完整流程（上传 → 问答 → 验证答案质量）
- [x] 添加多轮对话支持（`POST /api/chat/conversation`）

### 阶段 2：Advanced RAG 功能
- [x] Query Rewriting（问题改写）
- [x] Metadata Filtering（元数据过滤，前端支持按文档过滤）
- [x] Re-ranking（结果重排序，当前为轻量规则版）
- [x] Hybrid Search（向量召回 + 关键词召回融合，当前为 SQL LIKE 基础版）

### 阶段 3：Agent 功能
- [ ] Tool-Calling 工具定义（搜索、总结、导出）
- [ ] Agent 服务实现
- [ ] 前端 Agent 界面

### 阶段 4：生产级功能
- [ ] 完整可观测性（OpenTelemetry + Prometheus + Grafana）
- [ ] Kubernetes 部署配置（K3s）
- [ ] 性能压测报告（Virtual Threads 收益）
- [ ] 单元测试 + 集成测试
- [ ] 异常处理完善

### 阶段 5：文档输出
- [ ] 技术博客 1：Java 21 Virtual Threads 在 RAG 场景的应用
- [ ] 技术博客 2：Spring AI Advisor 体系工程化落地
- [ ] 技术博客 3：PostgreSQL pgvector 性能优化实践
- [ ] 项目总结文档（已完成初稿）

---

## 🔧 当前运行状态

### 后端服务
- **应用状态**：以本机实际 health check 为准（IDEA 重启后验证）
- **端口**：http://localhost:8080
- **Profile**：hybrid
- **日志位置**：IDEA Run 控制台；命令行启动时可重定向到日志文件

### 数据库
- **PostgreSQL**：✅ 运行中（localhost:5432）
- **Redis**：✅ 运行中（localhost:6379）
- **Ollama**：✅ 运行中（localhost:11434）

### Docker 容器
```bash
# 查看容器状态
docker ps

# 停止所有容器
docker-compose -f docker-compose-minimal.yml down

# 重启容器
docker-compose -f docker-compose-minimal.yml up -d
```

---

## 🚀 快速命令

### 启动应用
```bash
# 方法 1：使用 Maven
cd e:/project/work/job/smartkb-java21-spring-ai-rag
export SPRING_PROFILES_ACTIVE=hybrid
mvn spring-boot:run

# 方法 2：后台运行
mvn spring-boot:run > /tmp/smartkb.log 2>&1 &
```

### 查看日志
```bash
# 实时查看
tail -f /tmp/smartkb.log

# 查看错误
tail -100 /tmp/smartkb.log | grep -i "error\|exception"

# 查看 OpenAI 配置
tail -100 /tmp/smartkb.log | grep "Base URL"
```

### 测试 API
```bash
# 上传文档
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@test-docs/virtual-threads-guide.md"

# 对话测试
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"Hello"}'

# 查看文档列表
curl http://localhost:8080/api/documents
```

### 数据库操作
```bash
# 连接 PostgreSQL
docker exec -it smartkb-postgres psql -U smartkb -d smartkb

# 查看向量数据
SELECT
  metadata->>'fileName' as file_name,
  COUNT(*) as chunk_count
FROM vector_store
GROUP BY metadata->>'fileName';

# 清空数据
DELETE FROM vector_store;
```

---

## 📁 关键文件位置

### 配置文件
- `src/main/resources/application.yml` - 主配置
- `src/main/resources/application-hybrid.yml` - 混合模式配置
- `docker-compose-minimal.yml` - Docker 配置

### Java 代码
- `config/OpenAiClientConfig.java` - **自定义 OpenAI 客户端（重要！）**
- `config/AdvisorConfig.java` - Spring AI Advisor 配置
- `config/VectorStoreConfig.java` - 向量存储配置
- `service/RagService.java` - RAG 核心服务
- `controller/SmartKbController.java` - REST API 接口

### 前端
- `src/main/resources/static/index.html` - 单页应用

---

## ⚡ 下次继续时

1. **首先确认服务状态**：
   ```bash
   curl http://localhost:8080/actuator/health
   ps aux | grep java
   ```

2. **测试多轮对话功能**：
   - 浏览器访问：http://localhost:8080
   - 提问："Java 21 Virtual Threads 适合解决什么问题？"
   - 继续追问："它和传统线程池相比有什么优势？"
   - 观察右上角会话 ID 是否保持不变

3. **验证 Advanced RAG 引用片段**：
   - 验证 `POST /api/chat/advanced`
   - 确认 Query Rewriting、Metadata Filtering、Re-ranking 的日志和效果
   - 前端切到 `Advanced` 后查看回答下方的引用片段折叠区
   - 若引用片段乱码，先删除旧文档并重新上传 `test-docs/advanced-rag-demo.md`

4. **如果还有问题**：
   - 查看日志：`tail -50 /tmp/smartkb.log`
   - 考虑切换到 Ollama 本地 Chat 模型（避免中转站兼容性问题）

---

**当前最紧急任务**：重启应用后回归验证 Hybrid Search、Advanced RAG 引用片段，然后继续做引用片段点击定位。

祝顺利！🚀
