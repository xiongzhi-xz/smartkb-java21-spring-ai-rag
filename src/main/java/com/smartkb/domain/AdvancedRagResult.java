package com.smartkb.domain;

import java.util.List;

/**
 * Advanced RAG 查询结果。
 *
 * @param answer         最终答案
 * @param rewrittenQuery 改写后的检索查询
 * @param sources        命中的来源文档
 * @param retrievedCount 最终参与生成的文档片段数
 */
public record AdvancedRagResult(
        String answer,
        String rewrittenQuery,
        List<String> sources,
        int retrievedCount) {
}
