# SmartKB 5 分钟演示脚本

这份脚本用于面试、项目展示或自测。目标是稳定展示 SmartKB 的核心价值：企业知识库文档上传、向量化、RAG 问答、文档片段查看，以及 Java 21 Virtual Threads 的工程亮点。

## 演示前检查

按 [STARTUP.md](STARTUP.md) 启动项目后，确认：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

浏览器打开：

```text
http://localhost:8080
```

建议准备测试文档：

```text
test-docs/virtual-threads-guide.md
```

## 讲解开场

建议话术：

```text
SmartKB 是一个企业级智能知识库预研项目，核心目标是把 RAG 从概念落到可运行的 Java 工程里。
它使用 Java 21 Virtual Threads 提升 IO 密集型任务并发能力，使用 Spring AI 组织 RAG 链路，
用 PostgreSQL + pgvector 存储向量，Embedding 走本地 Ollama，Chat 走 OpenAI 兼容中转站。
```

重点讲清楚当前架构：

```text
文档上传 -> 文档解析/切片 -> Ollama Embedding -> pgvector 存储 -> 相似度检索 -> LLM 生成答案
```

## 演示步骤

### 1. 展示首页

打开：

```text
http://localhost:8080
```

说明：

- 左侧是文档管理
- 右侧是知识库问答
- 底部显示 Virtual Threads 已启用

### 2. 上传文档

点击 `上传文档`，选择：

```text
test-docs/virtual-threads-guide.md
```

观察：

- 左侧出现上传中状态
- 上传成功后显示生成的向量块数量
- 文档列表自动刷新

建议话术：

```text
上传后，后端会完成文档解析、切片、Embedding 生成和 pgvector 入库。
这里的文档处理和请求线程都可以受益于 Java 21 Virtual Threads，因为它们主要是 IO 密集任务。
```

### 3. 查看文档片段

点击左侧已上传文档。

展示：

- 右侧详情抽屉
- chunk 数量
- 每个 chunk 的文本内容
- 拖动详情窗口左边缘调整宽度

建议话术：

```text
这里展示的是进入向量库的实际片段。RAG 系统的效果很依赖切片质量，所以我把 chunk 详情做成可视化，
方便调试检索质量，而不是只把文档上传当成黑盒。
```

### 4. 知识库问答

在输入框提问：

```text
Java 21 Virtual Threads 适合解决什么问题？
```

预期答案应围绕：

- 高并发
- IO 密集场景
- 同步代码风格
- 文档解析、Embedding、数据库访问等 AI 工程场景

建议追问：

```text
它在 AI 应用中有什么实际价值？
```

```text
Virtual Threads 和传统线程池相比有什么优势？
```

继续追问时，页面会复用当前会话 ID 调用 `/api/chat/conversation`，用于展示多轮上下文能力。点击右上角 `新会话` 会清空当前 conversationId。

建议话术：

```text
这个回答不是普通聊天，而是先从 pgvector 检索相关文档片段，再把上下文注入给模型生成答案。
```

### 5. Advanced RAG 文档过滤

在页面右上角切到 `Advanced`，下拉选择已上传文档，继续提问：

```text
这份文档里提到 Virtual Threads 的核心价值是什么？
```

观察后端日志：

- `步骤 1: 查询改写`
- `步骤 3: 元数据过滤`
- `步骤 4: 结果重排序`

页面回答下方会显示命中片段数、改写后的查询和参考来源。

建议话术：

```text
Advanced RAG 不是直接把用户问题拿去检索，而是先做查询改写，再限定检索范围，最后对结果重排序。
这个入口方便演示指定文档范围内的高精度问答。
```

### 6. 展示后端验证接口

可选，用于证明 RAG 链路不是只靠前端：

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8080/api/test/rag `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body '{"question":"Java 21 Virtual Threads 适合解决什么问题？"}'
```

重点看：

- `retrievedDocsCount`
- `retrievedDocs`
- `isVirtualThread`
- `answer`

## 技术亮点讲法

### Java 21 Virtual Threads

```text
项目里文档解析、Embedding、数据库查询和模型调用都是典型 IO 密集场景。
Virtual Threads 让我可以保留同步代码风格，同时提升并发处理能力，避免传统线程池在阻塞 IO 下浪费平台线程。
```

### Spring AI Advisor

```text
Spring AI 的 Advisor 适合把检索、上下文注入、Prompt 组织这些横切逻辑工程化，
避免所有 RAG 逻辑都散落在 Controller 或 Service 里。
```

### 混合模型架构

```text
Embedding 放本地 Ollama，降低成本并保护文档数据；Chat 走中转站模型，保证回答质量。
这是一种适合个人预研和企业 PoC 的折中架构。
```

### pgvector

```text
PostgreSQL + pgvector 的好处是向量数据和业务元数据可以在同一个数据库里管理，
对 Java 后端项目来说部署和运维成本比单独引入专用向量数据库更低。
```

## 常见演示问题

### 回答慢

说明：

```text
当前 Chat 走外部中转站，响应时间受模型和网络影响。RAG 检索本身一般较快，主要耗时在模型生成。
```

可以让问题更短：

```text
用 100 字说明 Virtual Threads 的优势。
```

### 文档列表为空

先确认上传是否成功，或调用：

```powershell
Invoke-RestMethod http://localhost:8080/api/documents
```

### 详情窗口没有更新

浏览器强刷：

```text
Ctrl + F5
```

### 端口冲突

见 [STARTUP.md](STARTUP.md) 的 `8080 端口被占用` 章节。

## 结束总结

建议话术：

```text
这个项目目前已经完成基础 RAG 闭环和可演示前端。
下一步会继续打磨 Hybrid Search、答案来源片段展示和性能压测数据，
并补充更完整的可观测性和性能数据，让它从可运行项目进一步变成作品级工程案例。
```
