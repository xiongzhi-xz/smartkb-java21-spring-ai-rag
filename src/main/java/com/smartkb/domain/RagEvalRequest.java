package com.smartkb.domain;

import java.util.List;

/**
 * RAG 评测请求。
 *
 * @param cases    自定义评测用例；为空时使用内置中文评测集
 * @param fileName 可选文档过滤；为空时使用每个用例的 expectedFileName
 * @param topK     普通向量召回的 Top-K
 */
public record RagEvalRequest(
        List<RagEvalCase> cases,
        String fileName,
        Integer topK) {
}
