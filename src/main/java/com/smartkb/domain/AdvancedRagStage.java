package com.smartkb.domain;

import java.util.Map;

/**
 * Advanced RAG 分阶段执行状态。
 *
 * @param stage   阶段标识
 * @param message 前端可展示的阶段文案
 * @param details 阶段附加信息
 */
public record AdvancedRagStage(
        String stage,
        String message,
        Map<String, Object> details) {
}
