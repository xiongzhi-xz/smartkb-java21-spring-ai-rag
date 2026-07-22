package com.smartkb.domain;

import java.util.UUID;

/** PostgreSQL 中保存的知识库文档事实。 */
public record KnowledgeDocument(
        UUID id,
        UUID knowledgeBaseId,
        String fileName,
        String contentType,
        String objectKey,
        String contentChecksum,
        long sizeBytes,
        int versionNo,
        KnowledgeDocumentStatus status) {
}
