package com.smartkb.domain;

import java.util.List;

/**
 * RAG 评测用例。
 *
 * @param id               用例编号
 * @param question         中文评测问题
 * @param expectedFileName 期望命中的文档
 * @param expectedKeywords 期望命中的章节关键词
 */
public record RagEvalCase(
        String id,
        String question,
        String expectedFileName,
        List<String> expectedKeywords) {
}
