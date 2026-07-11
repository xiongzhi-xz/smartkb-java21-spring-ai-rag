package com.smartkb.domain;

/**
 * RAG 回答引用的文档片段。
 *
 * @param fileName 来源文件名
 * @param chunkId  片段 ID
 * @param preview  片段内容预览
 */
public record ReferenceChunk(
        String fileName,
        String chunkId,
        String preview) {
}
