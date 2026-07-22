package com.smartkb.application;

import com.smartkb.application.port.outbound.DocumentIngestionRepository;
import com.smartkb.application.port.outbound.IngestionJobPublisher;
import com.smartkb.application.port.outbound.ObjectStorage;
import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.IngestionRequestedEvent;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/** 失败文档入库任务的受保护重试编排。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRetryService {

    private final DocumentIngestionRepository documentIngestionRepository;
    private final ObjectStorage objectStorage;
    private final IngestionJobPublisher ingestionJobPublisher;

    public DocumentRetryResult retry(UUID documentId) {
        DocumentIngestionSubmission current = documentIngestionRepository.findLatest(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));
        if (current.job().status() == IngestionJobStatus.RETRYING) {
            return result(current, false);
        }
        if (current.job().status() != IngestionJobStatus.FAILED
                || current.document().status() != KnowledgeDocumentStatus.FAILED) {
            throw new IllegalStateException("文档当前不可重试: " + documentId + ", status=" + current.job().status());
        }

        verifyObject(current.document());
        var retryingTransition = documentIngestionRepository
                .markRetrying(current.job().id(), current.document().id());
        if (retryingTransition.isEmpty()) {
            return documentIngestionRepository.findLatest(documentId)
                    .filter(submission -> submission.job().status() == IngestionJobStatus.RETRYING)
                    .map(submission -> result(submission, false))
                    .orElseThrow(() -> new IllegalStateException("文档重试状态迁移失败: " + documentId));
        }
        DocumentIngestionSubmission retrying = retryingTransition.get();

        try {
            ingestionJobPublisher.publish(toEvent(retrying));
            log.info("失败文档已重新提交入库: documentId={}, jobId={}, retryCount={}",
                    documentId, retrying.job().id(), retrying.job().retryCount());
            return result(retrying, true);
        } catch (RuntimeException publishFailure) {
            String message = publishFailure.getMessage() == null
                    ? publishFailure.getClass().getSimpleName()
                    : publishFailure.getMessage();
            try {
                boolean compensated = documentIngestionRepository.markRetryPublishFailed(
                        retrying.job().id(), retrying.document().id(), "RETRY_PUBLISH_FAILED", message);
                if (!compensated) {
                    publishFailure.addSuppressed(new IllegalStateException("重试任务补偿失败: " + documentId));
                }
            } catch (RuntimeException compensationFailure) {
                publishFailure.addSuppressed(compensationFailure);
            }
            throw new IllegalStateException("重试任务发布失败: " + documentId, publishFailure);
        }
    }

    private void verifyObject(KnowledgeDocument document) {
        try (InputStream inputStream = objectStorage.get(document.objectKey())) {
            if (inputStream == null) {
                throw new IllegalStateException("原始对象不存在: " + document.objectKey());
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("原始对象不可用: " + document.objectKey(), e);
        } catch (Exception e) {
            throw new IllegalStateException("原始对象不可用: " + document.objectKey(), e);
        }
    }

    private IngestionRequestedEvent toEvent(DocumentIngestionSubmission submission) {
        KnowledgeDocument document = submission.document();
        return new IngestionRequestedEvent(
                submission.job().id(),
                document.id(),
                submission.job().idempotencyKey(),
                document.objectKey(),
                document.fileName(),
                fileType(document.fileName()));
    }

    private String fileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            throw new IllegalStateException("文档文件类型不可识别: " + fileName);
        }
        String type = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!type.matches("pdf|docx?|md|txt")) {
            throw new IllegalStateException("文档文件类型不可重试: " + type);
        }
        return type;
    }

    private DocumentRetryResult result(DocumentIngestionSubmission submission, boolean queued) {
        return new DocumentRetryResult(
                submission.document().id(),
                submission.job().id(),
                submission.job().status(),
                submission.job().retryCount(),
                queued);
    }
}
