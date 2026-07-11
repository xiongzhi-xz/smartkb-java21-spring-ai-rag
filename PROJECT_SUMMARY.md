# SmartKB 项目摘要

## 定位

Java 21 + Spring AI 可解释 RAG 知识库，用于展示完整的 AI 应用工程实践。

## 能力

- 文档解析、切片、Embedding 与 pgvector
- Hybrid Search、查询改写、BGE + 规则 RRF 融合重排
- 低置信度拒答与 Faithfulness / Answer Relevance / Context Relevance 评估
- SSE 流式回答与引用 chunk 定位
- Redis 多轮会话记忆
- Recall@K、Top1、MRR 与引用覆盖评测
- Micrometer、Prometheus、Grafana
- Docker Compose 和 K3s 演示部署

## 最值得讲的三个问题

1. 如何提高中文技术文档的检索稳定性。
2. 如何让 RAG 答案可追溯、可评测。
3. 如何补齐会话状态、监控和本地交付。

## 设计边界

- 重排是规则评分，不是 Cross-Encoder。
- 评测以检索质量为主，不代表完整答案质量。
- 部署能力用于演示，不宣称生产级高可用。

## 推荐验证

```bash
mvn test
git diff --check
```
