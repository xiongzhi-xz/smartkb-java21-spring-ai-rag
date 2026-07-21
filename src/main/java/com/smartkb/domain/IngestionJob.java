package com.smartkb.domain;

import java.util.UUID;

/** 文档入库任务的领域事实。 */
public record IngestionJob(UUID id, UUID documentId, String idempotencyKey, IngestionJobStatus status, int retryCount) {
}
