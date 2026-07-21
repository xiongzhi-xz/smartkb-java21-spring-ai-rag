package com.smartkb.domain;

import java.util.UUID;

/** 文档已接收，等待异步解析和索引的领域事件。 */
public record IngestionRequestedEvent(UUID jobId, UUID documentId, String idempotencyKey) {
}
