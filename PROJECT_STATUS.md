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

## 下一步

- [ ] 升级稳定版 Spring AI
- [ ] 接入模型 reranker 并进行指标对比
- [ ] 增加答案质量评测
- [ ] 拆分大体积 Controller 和 RAG 编排类
