package com.smartkb.application;

import com.smartkb.application.port.outbound.DocumentIngestionRepository;
import com.smartkb.application.port.outbound.IngestionJobPublisher;
import com.smartkb.application.port.outbound.ObjectStorage;
import com.smartkb.domain.DocumentIngestionSubmission;
import com.smartkb.domain.IngestionJob;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.domain.IngestionRequestedEvent;
import com.smartkb.domain.KnowledgeDocument;
import com.smartkb.domain.KnowledgeDocumentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * 接收原始文件并提交异步入库任务。
 *
 * 数据库先原子准备文档与任务；PENDING 任务可以安全重放对象写入和消息发布，
 * 消费端通过联合状态迁移避免同一任务被重复索引。
 */
@Slf4j
@Service
public class DocumentIngestionSubmissionService {

    private static final int INITIAL_VERSION = 1;
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final DocumentIngestionRepository documentIngestionRepository;
    private final ObjectStorage objectStorage;
    private final IngestionJobPublisher ingestionJobPublisher;
    private final UUID knowledgeBaseId;
    private final String knowledgeBaseName;

    public DocumentIngestionSubmissionService(
            DocumentIngestionRepository documentIngestionRepository,
            ObjectStorage objectStorage,
            IngestionJobPublisher ingestionJobPublisher,
            @Value("${smartkb.knowledge-base.id:00000000-0000-0000-0000-000000000001}") String knowledgeBaseId,
            @Value("${smartkb.knowledge-base.name:Default Knowledge Base}") String knowledgeBaseName) {
        this.documentIngestionRepository = documentIngestionRepository;
        this.objectStorage = objectStorage;
        this.ingestionJobPublisher = ingestionJobPublisher;
        this.knowledgeBaseId = UUID.fromString(knowledgeBaseId);
        this.knowledgeBaseName = requireText(knowledgeBaseName, "knowledgeBaseName");
    }

    public DocumentUploadResult submit(
            InputStreamSource content,
            String originalFileName,
            String fileType,
            String contentType,
            long sizeBytes) {
        if (content == null) {
            throw new IllegalArgumentException("文件内容不能为空");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String fileName = normalizeFileName(originalFileName);
        String normalizedFileType = normalizeFileType(fileType);
        String normalizedContentType = normalizeContentType(contentType);
        String checksum = calculateSha256(content);
        String idempotencyKey = "upload:" + knowledgeBaseId + ":" + checksum + ":v" + INITIAL_VERSION;

        UUID requestedDocumentId = UUID.randomUUID();
        UUID requestedJobId = UUID.randomUUID();
        String objectKey = "documents/" + knowledgeBaseId + "/" + requestedDocumentId + "/" + fileName;

        KnowledgeDocument requestedDocument = new KnowledgeDocument(
                requestedDocumentId,
                knowledgeBaseId,
                fileName,
                normalizedContentType,
                objectKey,
                checksum,
                sizeBytes,
                INITIAL_VERSION,
                KnowledgeDocumentStatus.PENDING);
        IngestionJob requestedJob = new IngestionJob(
                requestedJobId,
                requestedDocumentId,
                idempotencyKey,
                IngestionJobStatus.PENDING,
                0);

        DocumentIngestionSubmission submission = documentIngestionRepository.prepare(
                requestedDocument,
                requestedJob,
                knowledgeBaseName);

        boolean queued = false;
        if (submission.job().status() == IngestionJobStatus.PENDING) {
            KnowledgeDocument document = submission.document();
            try (InputStream inputStream = content.getInputStream()) {
                objectStorage.put(
                        document.objectKey(),
                        inputStream,
                        document.sizeBytes(),
                        document.contentType());
            } catch (IOException e) {
                throw new IllegalStateException("读取上传文件失败: " + fileName, e);
            }

            ingestionJobPublisher.publish(new IngestionRequestedEvent(
                    submission.job().id(),
                    document.id(),
                    submission.job().idempotencyKey(),
                    document.objectKey(),
                    document.fileName(),
                    extensionOf(document.fileName(), normalizedFileType)));
            queued = true;
            log.info("文档已提交异步入库: documentId={}, jobId={}, fileName={}",
                    document.id(), submission.job().id(), document.fileName());
        } else {
            log.info("复用已有入库任务: documentId={}, jobId={}, status={}",
                    submission.document().id(), submission.job().id(), submission.job().status());
        }

        return new DocumentUploadResult(
                submission.document().id(),
                submission.job().id(),
                submission.document().fileName(),
                submission.job().status(),
                queued);
    }

    private String calculateSha256(InputStreamSource content) {
        try (InputStream inputStream = content.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new IllegalStateException("计算文件校验和失败", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }

    private String normalizeFileName(String originalFileName) {
        String candidate = requireText(originalFileName, "fileName").replace('\\', '/');
        String fileName = candidate.substring(candidate.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (fileName.length() > 512) {
            throw new IllegalArgumentException("文件名不能超过 512 个字符");
        }
        return fileName;
    }

    private String normalizeFileType(String fileType) {
        String normalized = requireText(fileType, "fileType").toLowerCase(Locale.ROOT);
        if (!normalized.matches("pdf|docx?|md|txt")) {
            throw new IllegalArgumentException("不支持的文件类型: " + normalized);
        }
        return normalized;
    }

    private String normalizeContentType(String contentType) {
        String normalized = contentType == null || contentType.isBlank()
                ? DEFAULT_CONTENT_TYPE
                : contentType.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Content-Type 不能超过 128 个字符");
        }
        return normalized;
    }

    private String extensionOf(String fileName, String fallback) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return normalizeFileType(fileName.substring(dotIndex + 1));
        }
        return fallback;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
