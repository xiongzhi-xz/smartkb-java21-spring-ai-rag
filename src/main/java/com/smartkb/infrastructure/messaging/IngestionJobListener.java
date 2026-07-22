package com.smartkb.infrastructure.messaging;

import com.smartkb.application.port.outbound.DocumentIngestionRepository;
import com.smartkb.application.port.outbound.ObjectStorage;
import com.smartkb.application.DocumentIndexingService;
import com.smartkb.application.IndexingFailureException;
import com.smartkb.domain.IngestionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 文档入库事件消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionJobListener {

    private final DocumentIngestionRepository documentIngestionRepository;
    private final ObjectStorage objectStorage;
    private final DocumentIndexingService documentIndexingService;

    @RabbitListener(queues = RabbitIngestionTopology.QUEUE)
    public void consume(IngestionRequestedEvent event) {
        if (!documentIngestionRepository.markProcessing(event.jobId(), event.documentId())) {
            log.info("跳过重复或无效入库事件: jobId={}", event.jobId());
            return;
        }

        try {
            Resource resource = objectStorageResource(event.objectKey(), event.fileName());
            documentIndexingService.index(documentIngestionRepository.requireDocument(event.documentId()),
                    resource, event.fileType());
            if (!documentIngestionRepository.markReady(event.jobId(), event.documentId())) {
                throw new IllegalStateException("入库任务无法迁移到 READY: " + event.jobId());
            }
            log.info("入库任务处理完成: jobId={}, documentId={}", event.jobId(), event.documentId());
        } catch (Exception e) {
            markFailed(event, e);
            log.error("入库任务处理失败: jobId={}, documentId={}",
                    event.jobId(), event.documentId(), e);
            throw new IllegalStateException("文档入库任务失败: " + event.jobId(), e);
        }
    }

    private Resource objectStorageResource(String objectKey, String fileName) {
        return new AbstractResource() {
            @Override
            public String getDescription() {
                return "object storage resource [" + objectKey + "]";
            }

            @Override
            public String getFilename() {
                return fileName;
            }

            @Override
            public InputStream getInputStream() {
                return objectStorage.get(objectKey);
            }
        };
    }

    private void markFailed(IngestionRequestedEvent event, Exception cause) {
        String errorMessage = cause.getMessage() == null
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
        try {
            if (!documentIngestionRepository.markFailed(
                    event.jobId(),
                    event.documentId(),
                    errorCode(cause),
                    errorMessage)) {
                log.warn("入库任务无法迁移到 FAILED: jobId={}", event.jobId());
            }
        } catch (Exception statusException) {
            cause.addSuppressed(statusException);
            log.error("记录入库任务失败状态时发生异常: jobId={}", event.jobId(), statusException);
        }
    }

    private String errorCode(Exception cause) {
        if (cause instanceof IndexingFailureException indexingFailure) {
            return indexingFailure.errorCode();
        }
        return "INGESTION_FAILED";
    }
}
