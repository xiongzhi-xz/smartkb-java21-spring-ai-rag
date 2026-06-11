package com.smartkb.domain;

/**
 * 文档处理状态枚举
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
public enum DocumentStatus {
    /**
     * 待处理
     */
    PENDING,

    /**
     * 处理中（解析+Embedding+入库）
     */
    PROCESSING,

    /**
     * 处理完成
     */
    COMPLETED,

    /**
     * 处理失败
     */
    FAILED
}
