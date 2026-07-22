package com.smartkb.domain;

/** 已在 PostgreSQL 中原子准备好的文档与入库任务。 */
public record DocumentIngestionSubmission(KnowledgeDocument document, IngestionJob job) {
}
