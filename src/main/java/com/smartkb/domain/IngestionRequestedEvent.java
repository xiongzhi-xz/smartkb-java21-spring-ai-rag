package com.smartkb.domain;

import java.util.UUID;

import static java.util.Objects.requireNonNull;

/** 文档已接收，等待异步解析和索引的领域事件。 */
public record IngestionRequestedEvent(
        UUID jobId,
        UUID documentId,
        String idempotencyKey,
        String objectKey,
        String fileName,
        String fileType) {

    public IngestionRequestedEvent {
        requireNonNull(jobId, "jobId 不能为空");
        requireNonNull(documentId, "documentId 不能为空");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(objectKey, "objectKey");
        requireText(fileName, "fileName");
        requireText(fileType, "fileType");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }
}
