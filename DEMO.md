# SmartKB 演示指南

## 演示目标

在 5 分钟内证明 SmartKB 不是简单聊天页面，而是一套可解释、可评测的 RAG 工程闭环。

## 演示准备

- 启动 PostgreSQL、Redis、Ollama 和 SmartKB。
- 确认 `test-docs/advanced-rag-demo.md` 可用。
- 浏览器打开 SmartKB 首页。
- 演示前清理旧会话，避免结果受历史数据影响。

## 5 分钟路径

### 1. 文档入库

上传 `advanced-rag-demo.md`，打开文档详情，说明：

> 文档先经过解析和切片，再通过本地 Ollama 生成 Embedding，最终写入 PostgreSQL 的 pgvector。每个 chunk 都保留文件名和 chunk ID，用于过滤和引用定位。

### 2. 普通问答

提问：

```text
查询改写在 Advanced RAG 中解决什么问题？
```

展示 SSE 流式输出，然后追问：

```text
它对后续检索有什么帮助？
```

说明同一 conversation ID 的消息通过 Redis ChatMemory 保存。

### 3. Advanced RAG

切换 Advanced 模式，选择指定文档并再次提问。

重点展示：

- 查询改写
- 双路召回
- 文档过滤
- BGE Cross-Encoder 与规则排名融合
- 证据置信度与低置信度拒答
- 生成阶段
- 各阶段耗时

说明当前重排是可解释规则评分，不冒充 Cross-Encoder。

### 4. 引用定位

展开引用片段，点击定位到文档详情中的对应 chunk。

说明：

> 这一步让答案能够追溯到原始上下文，既方便用户核对，也为后续评测和问题排查提供证据。

### 5. 检索评测

运行内置评测，展示普通检索与 Advanced RAG 的：

- Recall@K
- Top1
- MRR
- 引用覆盖率

说明：

> 我没有只凭主观感受判断检索效果，而是用固定问题和期望 chunk 做回归。后续替换检索或重排算法，可以直接比较指标变化。

## 30 秒项目介绍

> SmartKB 是一个 Java 25 + Spring AI 的可解释 RAG 知识库。我实现了从文档解析、Embedding、pgvector 入库到 Hybrid Search、查询改写、BGE 融合重排、流式回答和引用定位的完整链路，并通过 Redis ChatMemory 保存多轮上下文。系统会计算检索证据置信度，在证据不足时跳过生成并明确拒答；同时提供 Recall@K、MRR 检索评测和 Faithfulness、Answer Relevance、Context Relevance 答案评估。

## 技术讨论

### 为什么不用纯向量检索？

向量检索对语义问题有效，但技术名词和精确章节容易漏召回。关键词检索补充精确匹配，两路结果合并后稳定性更好。

### 重排是模型吗？

不是。当前根据关键词、标题和领域锚点进行可解释评分。选择它是为了保持本地演示稳定；引入模型 reranker 前需要先用现有评测集证明收益。

### 虚拟线程提升了多少？

只说明它降低 IO 密集并发任务的线程成本。没有可信压测数据时，不声称固定倍数提升。

### Redis 不可用怎么办？

初始化 ChatMemory 时会尝试 Redis，连接失败时降级为内存实现，保证问答功能仍可使用，但重启后上下文不会保留。

### 如何判断 RAG 效果？

检索层使用 Recall@K、Top1、MRR 和引用覆盖做固定用例回归；答案层通过 LLM-as-Judge 评估 Faithfulness、Answer Relevance 和 Context Relevance。Judge 结果用于版本对比和问题定位，不替代人工标注。

### 项目离生产还有什么距离？

缺少多租户、权限、审计、生产级 Secret、HA、完整答案评测和容量压测。项目定位是可运行的工程原型，不包装成生产系统。

## 边界说明

- 不说“企业生产级已经落地”。
- 不把小型定制评测集结果外推为通用模型效果。
- 不使用没有测试记录的 QPS 或性能倍数。
- 不把 K3s 演示清单描述成生产级 Kubernetes 方案。
