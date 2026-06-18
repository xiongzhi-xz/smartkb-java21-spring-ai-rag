# SmartKB RAG 测试指南

## 测试环境准备

### 1. 启动基础设施
```bash
docker-compose up -d
```

验证服务启动：
```bash
docker ps
# 应该看到 postgres、redis、prometheus、grafana 四个容器
```

### 2. 配置 OpenAI API Key

**方式一：环境变量（推荐）**
```bash
export OPENAI_API_KEY=sk-your-api-key-here
```

**方式二：修改 application.yml**
```yaml
spring:
  ai:
    openai:
      api-key: sk-your-api-key-here
```

### 3. 启动应用
```bash
mvn spring-boot:run
```

启动成功标志：
```
[Virtual Threads] 初始化 PgVectorStore...
[Virtual Threads] 初始化 ChatClient with Advisor 链
Started SmartKbApplication in X.XXX seconds
```

---

## 测试流程

### 步骤 1：上传测试文档

**API:** `POST http://localhost:8080/api/documents/upload`

**Postman 配置：**
- Method: POST
- URL: http://localhost:8080/api/documents/upload
- Body: form-data
  - Key: `file` (类型选择 File)
  - Value: 选择一个 PDF/TXT/Markdown 文件

**cURL 示例：**
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@./test-doc.pdf"
```

**预期响应：**
```json
{
  "success": true,
  "fileName": "test-doc.pdf",
  "fileType": "pdf",
  "chunkCount": 15,
  "message": "文档上传成功"
}
```

**预期日志（关键点）：**
```
[Virtual Threads] 文档处理开始 | 文件: test-doc.pdf | 是否虚拟线程: ✓ YES
[Virtual Threads] 文档解析阶段 | 是否虚拟线程: ✓ YES
文档切片完成: 15 chunks
[Virtual Threads] Embedding生成阶段 | 是否虚拟线程: ✓ YES
开始批量生成 Embedding: 15 个文档
分为 2 个批次，每批 10 个文档
[Virtual Threads] Embedding批次处理 | 批次大小: 10 | 是否虚拟线程: ✓ YES
批量 Embedding 完成: 15 个文档, 耗时: 2500 ms, 平均: 166 ms/doc
[Virtual Threads] 向量存储阶段 | 是否虚拟线程: ✓ YES
文档处理完成: test-doc.pdf, 15 chunks, 耗时: 3200 ms
```

---

### 步骤 2：测试 RAG 查询（调试接口）

**API:** `POST http://localhost:8080/api/test/rag`

**Postman 配置：**
- Method: POST
- URL: http://localhost:8080/api/test/rag
- Headers: `Content-Type: application/json`
- Body (raw JSON):
```json
{
  "question": "文档的主要内容是什么？"
}
```

**cURL 示例：**
```bash
curl -X POST http://localhost:8080/api/test/rag \
  -H "Content-Type: application/json" \
  -d '{"question": "文档的主要内容是什么？"}'
```

**预期响应：**
```json
{
  "threadName": "tomcat-handler-5",
  "isVirtualThread": "✓ YES",
  "threadId": 45,
  "retrievedDocsCount": 3,
  "retrievalDuration": "120 ms",
  "retrievedDocs": [
    {
      "index": 1,
      "content": "这是检索到的第一段文档内容...",
      "metadata": {
        "fileName": "test-doc.pdf",
        "fileType": "pdf"
      }
    },
    {
      "index": 2,
      "content": "这是检索到的第二段文档内容...",
      "metadata": {
        "fileName": "test-doc.pdf",
        "fileType": "pdf"
      }
    }
  ],
  "answer": "根据文档内容，主要讲述了...",
  "answerLength": 150,
  "chatDuration": "1800 ms",
  "totalQueryDuration": "1950 ms",
  "totalDuration": "1950 ms",
  "success": true
}
```

**预期日志（关键点）：**
```
=== RAG 测试接口调用 ===
问题: 文档的主要内容是什么？
=== RAG 测试查询开始 ===
[Virtual Threads] RAG测试查询 | 问题: 文档的主要内容是什么？ | 是否虚拟线程: ✓ YES
执行向量检索...
检索完成: 找到 3 条相关文档，耗时: 120 ms
文档 #1: 这是检索到的第一段文档内容...
文档 #2: 这是检索到的第二段文档内容...
调用 ChatClient 生成答案...
答案生成完成，耗时: 1800 ms
=== RAG 测试查询完成 ===
总耗时: 1950 ms (检索: 120 ms, 生成: 1800 ms)
```

---

### 步骤 3：测试普通问答接口

**API:** `POST http://localhost:8080/api/chat`

**Body (raw JSON):**
```json
{
  "question": "Virtual Threads 有什么优势？"
}
```

**预期响应：**
```json
{
  "answer": "根据文档内容，Virtual Threads 的优势包括...",
  "success": true
}
```

---

## 验证 RAG 是否真正生效

### ✅ 判断标准

#### 1. **Virtual Threads 生效**
- 日志中出现 `是否虚拟线程: ✓ YES`
- 线程名称类似 `tomcat-handler-X` 或 `virtual-X`

#### 2. **向量检索生效**
- `/api/test/rag` 返回的 `retrievedDocsCount > 0`
- 日志中出现 `检索完成: 找到 X 条相关文档`
- `retrievedDocs` 字段包含文档片段

#### 3. **Advisor 链路生效**
- LLM 生成的答案中**引用了文档内容**（而非凭空编造）
- 例如：
  - ❌ 错误：LLM 基于训练数据回答（没有使用文档）
  - ✅ 正确：LLM 回答中明确提到"根据文档内容..."

#### 4. **性能提升验证**
- 批量上传多个文档时，`耗时 / 文档数量` 明显低于串行处理
- 例如：10 个文档，Virtual Threads 耗时约 5-10 秒（串行需要 30+ 秒）

---

## 常见问题排查

### 问题 1：文档上传后，查询返回 `retrievedDocsCount: 0`

**原因：**
- pgvector 表未正确初始化
- Embedding 未成功生成

**排查：**
```bash
# 连接 PostgreSQL
docker exec -it smartkb-postgres psql -U smartkb -d smartkb

# 检查向量表
\dt
SELECT COUNT(*) FROM vector_store;

# 应该看到文档数量 > 0
```

### 问题 2：日志显示 `是否虚拟线程: ✗ NO`

**原因：**
- `application.yml` 中 `spring.threads.virtual.enabled: true` 未生效

**排查：**
```bash
# 检查配置
grep -r "virtual" src/main/resources/application.yml

# 确认 Spring Boot 版本 >= 3.2
mvn dependency:tree | grep spring-boot
```

### 问题 3：Advisor 未生效，LLM 未使用文档内容

**原因：**
- `QuestionAnswerAdvisor` 未正确配置
- `VectorStore` 注入失败

**排查：**
- 查看启动日志中是否有 `初始化 ChatClient with Advisor 链`
- 查看 `AdvisorConfig` 是否被加载

---

## 性能基准参考

### 文档上传（10个PDF，每个5页）
- **串行处理**：约 30-40 秒
- **Virtual Threads**：约 8-12 秒
- **性能提升**：约 3-4 倍

### RAG 查询
- **向量检索**：50-200 ms（取决于文档数量）
- **LLM 生成**：1-3 秒（取决于 GPT 模型响应时间）
- **总耗时**：1.5-3.5 秒

---

## 工作台 smoke

Project Intake 接管报告与 Project Intake / Code Context 摘要指标可以用本地静态页面 + mocked API 做浏览器回归：

```powershell
node .\scripts\smoke\workbench-summary-smoke.mjs
```

Docker 服务重建后，也可以指向运行中的首页，确认容器内静态资源已经更新：

```powershell
node .\scripts\smoke\workbench-summary-smoke.mjs "http://localhost:8082/?v=summary-smoke"
```

脚本会启动本机 Chrome headless，检查 Project Intake 接管报告 6 行、摘要指标卡片数量、关键数值和 390px 视口横向溢出。

移动端边界交互可以用下面的脚本覆盖 390px 视口下的长文本、必填错误提示、导航和按钮宽度：

```powershell
node .\scripts\smoke\workbench-mobile-edge-smoke.mjs
```

Docker 服务重建后也可以指向运行中的首页：

```powershell
node .\scripts\smoke\workbench-mobile-edge-smoke.mjs "http://localhost:8082/?v=mobile-edge-smoke"
```

---

## 下一步

测试通过后，可以：
1. ✅ 提交代码到 GitHub
2. ✅ 编写性能压测报告
3. ✅ 部署到 Kubernetes（K3s）
4. ✅ 配置 Grafana 监控面板
