package com.smartkb.application;

import com.smartkb.application.port.outbound.DocumentDeletionRepository;
import com.smartkb.application.port.outbound.DocumentIndexCleaner;
import com.smartkb.application.port.outbound.ObjectStorage;
import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.KnowledgeDocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 企业文档、当前兼容索引和原文件的一致性删除编排。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentDeletionService {

    private final DocumentDeletionRepository documentDeletionRepository;
    private final DocumentIndexCleaner documentIndexCleaner;
    private final ObjectStorage objectStorage;

    @Transactional
    public DocumentDeletionResult delete(UUID documentId) {
        DocumentIngestionSubmission submission = documentDeletionRepository.lockLatest(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));
        requireDeletable(submission);

        int deletedChunks = documentIndexCleaner.deleteByDocumentId(documentId);
        objectStorage.delete(submission.document().objectKey());
        if (!documentDeletionRepository.delete(documentId, submission.job().id())) {
            throw new DocumentDeletionConflictException("文档删除状态保护失效: " + documentId);
        }

        log.info("企业文档已删除: documentId={}, jobId={}, deletedChunks={}",
                documentId, submission.job().id(), deletedChunks);
        return new DocumentDeletionResult(documentId, submission.document().fileName(), deletedChunks);
    }

    private void requireDeletable(DocumentIngestionSubmission submission) {
        KnowledgeDocumentStatus documentStatus = submission.document().status();
        IngestionJobStatus jobStatus = submission.job().status();
        boolean ready = documentStatus == KnowledgeDocumentStatus.READY
                && jobStatus == IngestionJobStatus.READY;
        boolean failed = documentStatus == KnowledgeDocumentStatus.FAILED
                && jobStatus == IngestionJobStatus.FAILED;
        if (!ready && !failed) {
            throw new DocumentDeletionConflictException(
                    "文档当前不可删除: " + submission.document().id()
                            + ", documentStatus=" + documentStatus
                            + ", jobStatus=" + jobStatus);
        }
    }
}
