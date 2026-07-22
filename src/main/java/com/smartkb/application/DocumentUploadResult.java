package com.smartkb.application;

import com.smartkb.domain.IngestionJobStatus;

import java.util.UUID;

/** 文档上传提交结果；queued 表示本次请求已写入对象并发布入库事件。 */
public record DocumentUploadResult(
        UUID documentId,
        UUID jobId,
        String fileName,
        IngestionJobStatus status,
        boolean queued) {
}
