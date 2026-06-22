package com.smartkb.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SmartKB 可观测性指标服务
 * <p>
 * 使用 Micrometer（Spring Boot Actuator 内置）注册自定义 Prometheus 指标，
 * 通过 /actuator/prometheus 端点暴露，供 Grafana 可视化。
 * <p>
 * 指标清单：
 * - smartkb_rag_advanced_requests_total：Advanced RAG 请求总数（计数器）
 * - smartkb_rag_advanced_requests_success：Advanced RAG 成功数
 * - smartkb_rag_advanced_requests_failed：Advanced RAG 失败数
 * - smartkb_rag_advanced_duration_seconds：Advanced RAG 总耗时（直方图）
 * - smartkb_rag_advanced_stage_duration_seconds：各阶段耗时（查询改写/召回/过滤/重排序/生成）
 * - smartkb_documents_uploaded_total：文档上传总数
 * - smartkb_documents_chunks_total：文档切片总数
 * - smartkb_chat_conversation_requests_total：对话模式请求总数
 * - smartkb_chat_ai_calls_total：AI 模型调用总数
 * - smartkb_chat_ai_call_duration_seconds：AI 模型调用耗时
 * <p>
 * 设计说明要点：
 * - 为什么用 Micrometer 而不是手动打点？
 *   Micrometer 是 Spring Boot 可观测性的标准抽象层，写一套代码就能输出到
 *   Prometheus/Datadog/CloudWatch 等多个后端，不绑定具体监控方案。
 * - Timer 和 Counter 的区别？
 *   Counter 只记录次数（单调递增），Timer 同时记录次数和耗时分布（含 P50/P95/P99）。
 *   RAG 各阶段适合用 Timer（既要看调用频次也要看耗时分布），上传计数适合用 Counter。
 * - 为什么要看 P95/P99 而不只看平均值？
 *   平均值会掩盖尾延迟；生产环境关心"95% 的请求在多少秒内完成"。
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Service
public class SmartKbMetricsService {

    // ===== Advanced RAG 指标 =====

    private final Counter advancedRagRequestsTotal;
    private final Counter advancedRagSuccessTotal;
    private final Counter advancedRagFailedTotal;
    private final Timer advancedRagDuration;
    private final Timer advancedRagRewriteDuration;
    private final Timer advancedRagRetrievalDuration;
    private final Timer advancedRagFilterDuration;
    private final Timer advancedRagRerankDuration;
    private final Timer advancedRagGenerationDuration;

    // ===== 文档管理指标 =====

    private final Counter documentsUploadedTotal;
    private final Counter documentsChunksTotal;

    // ===== 对话指标 =====

    private final Counter conversationRequestsTotal;

    // ===== AI 模型调用指标 =====

    private final Counter aiCallsTotal;
    private final Timer aiCallDuration;

    public SmartKbMetricsService(MeterRegistry registry) {
        log.info("初始化 SmartKB 可观测性指标");

        // --- Advanced RAG 指标 ---
        advancedRagRequestsTotal = Counter.builder("smartkb.rag.advanced.requests")
                .description("Advanced RAG 请求总数")
                .register(registry);

        advancedRagSuccessTotal = Counter.builder("smartkb.rag.advanced.requests.success")
                .description("Advanced RAG 请求成功数")
                .register(registry);

        advancedRagFailedTotal = Counter.builder("smartkb.rag.advanced.requests.failed")
                .description("Advanced RAG 请求失败数")
                .register(registry);

        advancedRagDuration = Timer.builder("smartkb.rag.advanced.duration")
                .description("Advanced RAG 总耗时")
                .publishPercentileHistogram()
                .register(registry);

        advancedRagRewriteDuration = Timer.builder("smartkb.rag.advanced.stage.duration")
                .description("Advanced RAG 各阶段耗时")
                .tag("stage", "rewrite")
                .register(registry);

        advancedRagRetrievalDuration = Timer.builder("smartkb.rag.advanced.stage.duration")
                .description("Advanced RAG 各阶段耗时")
                .tag("stage", "retrieval")
                .register(registry);

        advancedRagFilterDuration = Timer.builder("smartkb.rag.advanced.stage.duration")
                .description("Advanced RAG 各阶段耗时")
                .tag("stage", "filter")
                .register(registry);

        advancedRagRerankDuration = Timer.builder("smartkb.rag.advanced.stage.duration")
                .description("Advanced RAG 各阶段耗时")
                .tag("stage", "rerank")
                .register(registry);

        advancedRagGenerationDuration = Timer.builder("smartkb.rag.advanced.stage.duration")
                .description("Advanced RAG 各阶段耗时")
                .tag("stage", "generation")
                .register(registry);

        // --- 文档管理指标 ---
        documentsUploadedTotal = Counter.builder("smartkb.documents.uploaded")
                .description("文档上传总数")
                .register(registry);

        documentsChunksTotal = Counter.builder("smartkb.documents.chunks")
                .description("文档切片总数")
                .register(registry);

        // --- 对话指标 ---
        conversationRequestsTotal = Counter.builder("smartkb.chat.conversation.requests")
                .description("对话模式请求总数")
                .register(registry);

        // --- AI 模型调用指标 ---
        aiCallsTotal = Counter.builder("smartkb.chat.ai.calls")
                .description("AI 模型调用总数")
                .register(registry);

        aiCallDuration = Timer.builder("smartkb.chat.ai.call.duration")
                .description("AI 模型调用耗时")
                .publishPercentileHistogram()
                .register(registry);
    }

    // ===== Advanced RAG 指标记录方法 =====

    /**
     * 记录一次 Advanced RAG 请求完成
     *
     * @param rewriteMs    查询改写耗时(ms)
     * @param retrievalMs  候选召回耗时(ms)
     * @param filterMs     过滤耗时(ms)
     * @param rerankMs     重排序耗时(ms)
     * @param generationMs 生成耗时(ms)
     * @param totalMs      总耗时(ms)
     * @param success      是否成功
     */
    public void recordAdvancedRagRequest(
            long rewriteMs, long retrievalMs, long filterMs,
            long rerankMs, long generationMs, long totalMs, boolean success) {

        advancedRagRequestsTotal.increment();
        if (success) {
            advancedRagSuccessTotal.increment();
        } else {
            advancedRagFailedTotal.increment();
        }

        // 记录总耗时（直方图，可看 P50/P95/P99）
        advancedRagDuration.record(java.time.Duration.ofMillis(totalMs));

        // 记录各阶段耗时
        if (rewriteMs > 0) advancedRagRewriteDuration.record(java.time.Duration.ofMillis(rewriteMs));
        if (retrievalMs > 0) advancedRagRetrievalDuration.record(java.time.Duration.ofMillis(retrievalMs));
        if (filterMs > 0) advancedRagFilterDuration.record(java.time.Duration.ofMillis(filterMs));
        if (rerankMs > 0) advancedRagRerankDuration.record(java.time.Duration.ofMillis(rerankMs));
        if (generationMs > 0) advancedRagGenerationDuration.record(java.time.Duration.ofMillis(generationMs));
    }

    // ===== 文档管理指标记录 =====

    /**
     * 记录一次文档上传
     *
     * @param chunkCount 切片数量
     */
    public void recordDocumentUpload(int chunkCount) {
        documentsUploadedTotal.increment();
        documentsChunksTotal.increment(chunkCount);
    }

    // ===== 对话指标记录 =====

    /**
     * 记录一次对话请求
     */
    public void recordConversationRequest() {
        conversationRequestsTotal.increment();
    }

    // ===== AI 模型调用指标记录 =====

    /**
     * 记录一次 AI 模型调用
     *
     * @param durationMs 调用耗时(ms)
     */
    public void recordAiCall(long durationMs) {
        aiCallsTotal.increment();
        if (durationMs > 0) {
            aiCallDuration.record(java.time.Duration.ofMillis(durationMs));
        }
    }
}
