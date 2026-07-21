package com.smartkb.domain;

/** 文档异步入库任务状态。 */
public enum IngestionJobStatus {
    PENDING, PROCESSING, RETRYING, READY, FAILED
}
