package com.smartkb.application.port.outbound;

import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.KnowledgeDocument;

import java.util.UUID;

/** 文档事实与入库任务的原子准备、联合状态迁移端口。 */
public interface DocumentIngestionRepository {

    DocumentIngestionSubmission prepare(
            KnowledgeDocument document,
            IngestionJob job,
            String knowledgeBaseName);

    boolean markProcessing(UUID jobId, UUID documentId);

    boolean markReady(UUID jobId, UUID documentId);

    boolean markFailed(UUID jobId, UUID documentId, String errorCode, String errorMessage);
}
