package com.smartkb.domain;

import java.util.List;

/**
 * RAG 评测报告。
 *
 * @param totalCases               用例总数
 * @param baselineHitCount         普通向量召回命中数
 * @param advancedHitCount         Advanced RAG 命中数
 * @param citationHitCount         引用片段命中数
 * @param advancedImprovementCount Advanced 命中但普通召回未命中的数量
 * @param baselineHitRate          普通向量召回命中率
 * @param advancedHitRate          Advanced RAG 命中率
 * @param citationHitRate          引用片段命中率
 * @param cases                    用例明细
 */
public record RagEvalReport(
        int totalCases,
        int baselineHitCount,
        int advancedHitCount,
        int citationHitCount,
        int advancedImprovementCount,
        double baselineHitRate,
        double advancedHitRate,
        double citationHitRate,
        List<RagEvalCaseResult> cases) {
}
