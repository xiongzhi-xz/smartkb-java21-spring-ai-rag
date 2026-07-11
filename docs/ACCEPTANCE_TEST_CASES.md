# SmartKB 验收测试用例

## 前置条件

- SmartKB：`http://localhost:8080`
- BGE Reranker：`http://localhost:8090`
- 已上传 `test-docs/advanced-rag-demo.md`
- Advanced 模式选择 `advanced-rag-demo.md`

## 用例 1：相关问题正常回答

问题：

```text
查询改写在 Advanced RAG 中解决什么问题？
```

预期结果：

- `refused=false`
- `confidence` 高于拒答阈值
- 返回至少一个引用片段
- 页面展示查询改写、召回、重排和生成阶段
- 回答内容说明口语化或模糊问题会被转换为更适合检索的表达

## 用例 2：无关问题触发拒答

问题：

```text
请根据知识库预测下周上海的天气。
```

预期结果：

- `refused=true`
- 页面显示证据置信度和拒答原因
- `generationMs=0` 或没有进入答案生成阶段
- 回答明确提示知识库证据不足，不编造天气信息

## 用例 3：答案质量 Judge

请求：

```http
POST /api/rag/eval/answer
Content-Type: application/json

{
  "question": "查询改写解决什么问题？",
  "answer": "查询改写将口语化问题转换为更适合检索的表达。",
  "contexts": [
    "查询改写会结合上下文，将问题改写为更适合检索的表达。"
  ]
}
```

预期结果：

- 返回 `faithfulness`
- 返回 `answerRelevance`
- 返回 `contextRelevance`
- 返回三项平均值 `overallScore`
- 每个维度包含评分理由

## 用例 4：引用片段定位

问题：

```text
为什么引用片段能提升 RAG 系统可信度？
```

预期结果：

- 回答下方展示引用片段
- 点击引用可以打开文档详情
- 页面定位并高亮对应 `chunkId`
- 引用内容能够支撑回答中的关键结论

## 用例 5：Reranker 故障降级

操作步骤：

1. 停止 `smartkb-reranker` 容器。
2. 再次执行相关问题。
3. 恢复 Reranker 容器。

预期结果：

- Reranker 不可用时问答接口仍然成功
- 重排模式变为 `rule-fallback`
- Prometheus 降级计数增加
- 恢复后重排模式重新变为 `bge-rule-hybrid`

## 用例 6：检索回归评测

操作：点击页面右上角“评测检索质量”，或调用：

```http
POST /api/rag/eval/run
Content-Type: application/json

{
  "topK": 5
}
```

预期结果：

- 返回 Recall@K、Top1、MRR 和引用覆盖率
- 返回每个测试问题的命中片段和失败原因
- 当前固定 8 用例中，融合重排结果应为 Top1 `8/8`、MRR `1.0`
- 该结果只代表项目内小型定制测试集

## 本次本地验收记录

- SmartKB：`UP`
- BGE Reranker：`UP`，设备为 `cuda`
- 相关问题：`refused=false`，`confidence=1.0`
- 无关问题：`refused=true`，`confidence=0.15`
- Judge：Faithfulness `0.95`、Answer Relevance `0.90`、Context Relevance `0.90`、Overall `0.9167`
