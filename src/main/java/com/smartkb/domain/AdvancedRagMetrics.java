package com.smartkb.domain;

/**
 * Advanced RAG 端到端耗时拆分。
 *
 * @param rewriteMs    查询改写耗时
 * @param retrievalMs  候选召回耗时
 * @param filterMs     元数据过滤耗时
 * @param rerankMs     重排序耗时
 * @param generationMs 生成回答耗时
 * @param totalMs      总耗时
 */
public record AdvancedRagMetrics(
        long rewriteMs,
        long retrievalMs,
        long filterMs,
        long rerankMs,
        long generationMs,
        long totalMs) {
}
