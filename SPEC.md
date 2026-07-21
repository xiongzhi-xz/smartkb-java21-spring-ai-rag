# SmartKB SPEC

## 当前目标

打造一个聚焦 Advanced RAG 的 Java 工程项目，完整展示检索、会话状态、评测、可观测性和交付能力。

## 功能范围

- [x] Markdown、TXT、PDF、DOCX 文档上传
- [x] 文档解析、切片、Embedding 和 pgvector 入库
- [x] 文档列表、详情、chunk 查看和删除
- [x] 普通多轮流式问答
- [x] Advanced RAG 分阶段流式问答
- [x] 查询改写
- [x] 向量与关键词双路召回
- [x] 文档元数据过滤
- [x] 可解释规则重排
- [x] 引用片段定位原文 chunk
- [x] Redis ChatMemory 与 TTL
- [x] Recall@K、Top1、MRR 和引用覆盖评测
- [x] Micrometer、Prometheus 和 Grafana
- [x] Docker Compose 和 K3s 演示部署
- [x] 单元测试与静态页面 smoke

## 不做什么

- 不实现 Agent 项目接管或自动开发平台。
- 不宣称当前规则重排等同于模型 reranker。
- 不实现多租户、RBAC、计费和生产级 Secret 管理。
- 不把演示环境的部署清单描述为生产级高可用方案。

## 核心链路

```text
Upload
  -> Parse
  -> Split
  -> Embed
  -> pgvector

Question
  -> Load conversation history
  -> Rewrite query
  -> Vector retrieval + keyword retrieval
  -> Merge + metadata filter
  -> Rule re-ranking
  -> Generate answer
  -> Return citations and stage metrics
```

## 数据模型

### 文档

- 文件名
- 文件类型
- 状态
- chunk 数量
- 上传时间

### 向量 chunk

- 内容
- embedding
- 文件名元数据
- chunk ID
- 内容类型和章节信息

### 会话记忆

- Redis key：conversation ID
- value：按时间排序的消息列表
- TTL：默认 24 小时，活跃读写续期

### 评测用例

- 问题
- 期望关键词
- 期望 chunk
- 检索 Top-K
- 普通与 Advanced 检索结果

## API 前缀

所有业务接口使用 `/api/`：

- `/api/documents/**`
- `/api/chat/**`
- `/api/rag/eval/**`

## 验收标准

- [x] 文档可以上传并生成可查询 chunks。
- [x] 普通与 Advanced 模式均能流式返回。
- [x] Advanced 模式能够展示各阶段状态和耗时。
- [x] 引用可以定位到文档详情中的对应 chunk。
- [x] 相同 conversation ID 可以恢复 Redis 中的上下文。
- [x] 评测接口能够输出 Recall@K、Top1、MRR 和引用覆盖。
- [x] Prometheus 能采集 RAG、自定义 AI 调用和文档指标。
- [x] `mvn test` 与 `git diff --check` 通过。

## 后续优化

- [ ] 升级到稳定版 Spring AI 并记录迁移差异。
- [ ] 接入 Cross-Encoder reranker，与规则重排进行 A/B 对比。
- [ ] 增加答案忠实度和上下文相关性评测。
- [x] 使用 Java 21 环境重新执行完整测试。
- [ ] 使用 Java 21 环境执行压力测试。
- [ ] 拆分体积较大的 Controller 与 Advanced RAG 编排类。

## BGE Reranker

### 目标

使用本地 GPU 模型对 Hybrid Search 候选片段进行语义重排，并保留现有规则重排作为故障降级路径。

### 技术方案

- 模型：`BAAI/bge-reranker-v2-m3`
- 推理服务：Python FastAPI
- 部署：Docker Compose + NVIDIA GPU
- 推理精度：FP16
- Java 调用：HTTP 批量请求
- 超时：默认 3 秒
- 降级：连接失败、超时、响应异常时自动使用规则重排

### 验收标准

- [x] Reranker 服务提供 `/health` 和 `/rerank`。
- [x] Java 能按照模型分数重排候选片段。
- [x] 模型服务不可用时问答链路仍能通过规则重排完成。
- [x] SSE 阶段信息能够展示本次使用的重排模式。
- [x] Prometheus 能统计模型重排次数、降级次数和耗时。
- [x] 检索评测可以在启用和关闭模型重排时进行指标对比。

## 置信度拒答与答案评测

- 基于重排后片段对查询关键词和领域锚点的覆盖度计算检索置信度。
- 置信度低于阈值时不调用生成模型，返回明确的证据不足提示。
- 返回结果包含置信度、是否拒答和拒答原因。
- 输入问题、答案和引用上下文，通过 LLM-as-Judge 输出忠实度、答案相关性和上下文相关性。
- Judge 只用于离线评测，不参与线上答案生成。
