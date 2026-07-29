# SmartKB 当前状态

## 已完成

- [x] 文档上传、解析、切片和向量入库
- [x] 普通多轮流式问答
- [x] Advanced RAG 分阶段流式问答
- [x] Hybrid Search、查询改写、过滤和 BGE + 规则 RRF 融合重排
- [x] 低置信度拒答与三维答案质量 Judge
- [x] 引用片段定位
- [x] Redis ChatMemory
- [x] RAG 检索质量评测
- [x] Prometheus 和 Grafana
- [x] Docker Compose 和 K3s 演示清单
- [x] 单元测试和浏览器 smoke
- [x] 本地 GPU BGE Reranker、规则降级和排名融合
- [x] 移除非核心 Agent 工作台，统一项目叙事

## 当前边界

- 重排使用可解释规则评分。
- 答案质量采用 LLM-as-Judge，适合回归对比，不等同于人工真值。
- 部署配置用于本地和演示环境。
- Spring AI 保持 1.0.0-M1；Java 25 正常模式已验证通过，不因版本标签而单独升级依赖。

## 当前待外部条件

- Lombok 1.18.40 在 JDK 25 的 `--sun-misc-unsafe-memory-access=deny` 模式下不兼容；已验证 1.18.46 也未解决，等待上游支持，不能用 JDK 内部模块开放参数掩盖问题。
- 最终模型回答验收依赖已配置的 OpenAI-compatible 中转服务返回完整 chat-completions JSON；当前仓库代码已采用已验证的 JDK URLConnection 传输，但不能替外部服务修复截断或空响应。

## 后续可选工程任务

- 在保持现有 API 契约的前提下拆分大体积 Controller 和 RAG 编排类。
- 当 Spring AI 或 Lombok 发布经 Java 25 实测兼容的版本时，单独评估升级并完整回归验证。
