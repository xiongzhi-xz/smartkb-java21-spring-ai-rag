package com.smartkb.domain;

import java.util.List;

/**
 * 单个 RAG 评测用例结果。
 *
 * @param caseId                  用例编号
 * @param question                评测问题
 * @param expectedFileName        期望文档
 * @param expectedKeywords        期望关键词
 * @param baselineHit             普通向量召回是否命中
 * @param advancedHit             Advanced RAG 检索链路是否命中
 * @param citationHit             Advanced RAG 引用片段是否覆盖期望关键词
 * @param baselineRetrievedCount  普通向量召回数量
 * @param advancedRetrievedCount  Advanced RAG 最终片段数量
 * @param rewrittenQuery          改写后的查询
 * @param baselineMatchedKeywords 普通向量召回命中的关键词
 * @param advancedMatchedKeywords Advanced RAG 命中的关键词
 * @param references              Advanced RAG 引用片段预览
 */
public record RagEvalCaseResult(
        String caseId,
        String question,
        String expectedFileName,
        List<String> expectedKeywords,
        boolean baselineHit,
        boolean advancedHit,
        boolean citationHit,
        int baselineRetrievedCount,
        int advancedRetrievedCount,
        String rewrittenQuery,
        List<String> baselineMatchedKeywords,
        List<String> advancedMatchedKeywords,
        List<ReferenceChunk> references) {
}
