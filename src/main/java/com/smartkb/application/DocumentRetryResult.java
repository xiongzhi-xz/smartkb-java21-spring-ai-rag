package com.smartkb.application;

import com.smartkb.domain.IngestionJobStatus;

import java.util.UUID;

/** 文档失败入库任务的重试结果。 */
public record DocumentRetryResult(
        UUID documentId,
        UUID jobId,
        IngestionJobStatus status,
        int retryCount,
        boolean queued) {
}
