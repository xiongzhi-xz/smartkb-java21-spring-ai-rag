package com.smartkb.service;

import com.smartkb.util.VirtualThreadInspector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * RAG 核心服务 - 知识库问答系统
 * <p>
 * 核心职责：
 * 1. 文档上传处理流程：解析 → Embedding → 向量存储
 * 2. RAG 检索问答：查询 → 向量检索 → LLM 生成答案
 * 3. 多轮对话支持：集成 ChatMemory 实现上下文记忆
 * <p>
 * 整合的服务：
 * - DocumentLoaderService：文档解析与切片
 * - EmbeddingService：批量 Embedding 生成（Virtual Threads 并发）
 * - VectorStoreService：向量存储与检索
 * - ChatClient + Advisor：Spring AI 问答链路
 * <p>
 * Virtual Threads 应用场景：
 * - 文档上传处理：使用虚拟线程池执行耗时任务（解析 + Embedding）
 * - 避免阻塞主线程（Controller 线程），提升并发处理能力
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final DocumentLoaderService documentLoaderService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final ChatClient chatClient;
    private final SmartKbMetricsService metricsService;

    /**
     * 添加文档到知识库（完整流程：解析 → Embedding → 存储）
     * <p>
     * 流程说明：
     * 1. 使用 DocumentLoaderService 解析文档并切片
     * 2. 使用 EmbeddingService 批量生成 Embedding（Virtual Threads 并发）
     * 3. 使用 VectorStoreService 存入 pgvector
     * <p>
     * Virtual Threads 优化：
     * - 整个处理流程在虚拟线程中执行（异步处理）
     * - 不阻塞 Controller 线程，支持高并发上传
     *
     * @param resource 文档资源
     * @param fileType 文件类型（pdf、docx、md、txt）
     * @param metadata 文档元数据（可选）
     * @return 处理的文档块数量
     */
    public int addDocument(Resource resource, String fileType, java.util.Map<String, Object> metadata) {
        log.info("开始处理文档: {}, 类型: {}", resource.getFilename(), fileType);
        VirtualThreadInspector.logThreadInfo("文档处理开始", "文件: " + resource.getFilename());
        long startTime = System.currentTimeMillis();

        try {
            // 1. 解析并切片文档
            VirtualThreadInspector.logThreadInfo("文档解析阶段");
            List<Document> chunks = documentLoaderService.loadAndSplitDocument(resource, fileType);
            log.info("文档切片完成: {} chunks", chunks.size());

            // 2. 添加元数据（文件名、类型等）
            if (metadata != null) {
                chunks.forEach(chunk -> chunk.getMetadata().putAll(metadata));
            }
            chunks.forEach(chunk -> {
                chunk.getMetadata().put("fileName", resource.getFilename());
                chunk.getMetadata().put("fileType", fileType);
            });

            // 3. 批量生成 Embedding（Virtual Threads 并发）
            VirtualThreadInspector.logThreadInfo("Embedding生成阶段");
            embeddingService.embedDocumentsBatch(chunks);
            log.info("Embedding 生成完成");

            // 4. 存入向量库
            VirtualThreadInspector.logThreadInfo("向量存储阶段");
            vectorStoreService.addDocuments(chunks);

            long duration = System.currentTimeMillis() - startTime;
            log.info("文档处理完成: {},  chunks, 耗时:  ms",
                    resource.getFilename(), chunks.size(), duration);
            VirtualThreadInspector.logThreadInfo("文档处理完成",
                    String.format("文件: %s, chunks: %d, 耗时: %d ms", resource.getFilename(), chunks.size(), duration));

            metricsService.recordDocumentUpload(chunks.size());
            return chunks.size();

        } catch (Exception e) {
            log.error("文档处理失败: {}", resource.getFilename(), e);
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量添加文档（使用 Virtual Threads 并发处理）
     * <p>
     * Virtual Threads 优化：
     * - 使用 newVirtualThreadPerTaskExecutor 为每个文档创建虚拟线程
     * - 并发解析 + Embedding + 存储，大幅提升批量上传性能
     *
     * @param resources 文档资源列表
     * @param fileType  文件类型
     * @return 处理的总文档块数量
     */
    public int addDocumentsBatch(List<Resource> resources, String fileType) {
        log.info("批量处理文档: {} 个文件, 类型: {}", resources.size(), fileType);
        VirtualThreadInspector.logThreadInfo("批量文档处理开始", "文件数: " + resources.size());
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            log.info("使用 Virtual Thread Executor 并发处理 {} 个文档", resources.size());

            // 为每个文档创建一个虚拟线程并发处理
            List<java.util.concurrent.Future<Integer>> futures = resources.stream()
                    .map(resource -> executor.submit(() -> {
                        VirtualThreadInspector.logThreadInfo("文档处理任务", "文件: " + resource.getFilename());
                        return addDocument(resource, fileType, null);
                    }))
                    .toList();

            // 等待所有文档处理完成
            int totalChunks = futures.stream()
                    .mapToInt(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            log.error("文档处理任务失败", e);
                            return 0;
                        }
                    })
                    .sum();

            long duration = System.currentTimeMillis() - startTime;
            log.info("批量文档处理完成: {} 个文件, {} chunks, 耗时: {} ms",
                    resources.size(), totalChunks, duration);
            VirtualThreadInspector.logThreadInfo("批量文档处理完成",
                    String.format("文件数: %d, 总chunks: %d, 耗时: %d ms", resources.size(), totalChunks, duration));

            return totalChunks;

        } catch (Exception e) {
            log.error("批量文档处理失败", e);
            throw new RuntimeException("批量文档处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * RAG 问答（单轮对话）
     * <p>
     * 流程说明：
     * 1. ChatClient 通过 RetrievalAugmentationAdvisor 自动进行向量检索
     * 2. 检索到的相关文档会注入到 LLM Prompt
     * 3. QuestionAnswerAdvisor 基于检索结果生成答案
     *
     * @param question 用户问题
     * @return 答案
     */
    public String query(String question) {
        log.info("RAG 问答: {}", question.substring(0, Math.min(50, question.length())));
        VirtualThreadInspector.logThreadInfo("RAG查询开始", "问题长度: " + question.length());

        try {
            // ChatClient 已配置 Advisor 链，会自动执行 RAG 流程
            String answer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();

            log.info("RAG 问答完成，答案长度: {}", answer.length());
            VirtualThreadInspector.logThreadInfo("RAG查询完成", "答案长度: " + answer.length());
            return answer;

        } catch (Exception e) {
            log.error("RAG 问答失败: {}", question, e);
            throw new RuntimeException("RAG 问答失败: " + e.getMessage(), e);
        }
    }

    /**
     * RAG 问答（多轮对话，支持上下文记忆）
     * <p>
     * 使用 MessageChatMemoryAdvisor 自动管理对话历史：
     * - 历史消息存储在 ChatMemory（基于 conversationId）
     * - Advisor 会自动注入历史上下文到 Prompt
     *
     * @param question       用户问题
     * @param conversationId 会话 ID（用于区分不同用户/会话）
     * @return 答案
     */
    public String queryWithContext(String question, String conversationId) {
        log.info("多轮 RAG 问答: conversationId={}, question={}",
                conversationId, question.substring(0, Math.min(50, question.length())));
        VirtualThreadInspector.logThreadInfo("多轮RAG查询开始",
                "conversationId: " + conversationId + ", 问题长度: " + question.length());

        try {
            // ChatClient 的 MessageChatMemoryAdvisor 会自动加载历史
            String answer = chatClient.prompt()
                    .user(question)
                    .advisors(a -> a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                    .call()
                    .content();

            log.info("多轮 RAG 问答完成: conversationId={}, 答案长度: {}", conversationId, answer.length());
            VirtualThreadInspector.logThreadInfo("多轮RAG查询完成",
                    "conversationId: " + conversationId + ", 答案长度: " + answer.length());
            return answer;

        } catch (Exception e) {
            log.error("多轮 RAG 问答失败: conversationId={}, question={}", conversationId, question, e);
            throw new RuntimeException("多轮 RAG 问答失败: " + e.getMessage(), e);
        }
    }

    /**
     * RAG 问答流式输出（多轮对话，支持上下文记忆）
     *
     * @param question       用户问题
     * @param conversationId 会话 ID
     * @return 增量答案片段
     */
    public Flux<String> queryWithContextStream(String question, String conversationId) {
        log.info("多轮 RAG 流式问答: conversationId={}, question={}",
                conversationId, question.substring(0, Math.min(50, question.length())));
        VirtualThreadInspector.logThreadInfo("多轮RAG流式查询开始",
                "conversationId: " + conversationId + ", 问题长度: " + question.length());

        return chatClient.prompt()
                .user(question)
                .advisors(a -> a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                .stream()
                .content()
                .doOnComplete(() -> {
                    log.info("多轮 RAG 流式问答完成: conversationId={}", conversationId);
                    VirtualThreadInspector.logThreadInfo("多轮RAG流式查询完成",
                            "conversationId: " + conversationId);
                })
                .doOnError(e -> log.error("多轮 RAG 流式问答失败: conversationId={}, question={}",
                        conversationId, question, e));
    }

    /**
     * 测试专用方法：带详细调试信息的 RAG 查询
     * <p>
     * 返回完整的调试信息，用于验证：
     * 1. Virtual Threads 是否生效
     * 2. 向量检索结果（Top-K 文档）
     * 3. 注入到 LLM 的上下文内容
     * 4. 最终生成的答案
     *
     * @param question 用户问题
     * @return 包含调试信息的 Map
     */
    public java.util.Map<String, Object> queryWithDebugInfo(String question) {
        log.info("=== RAG 测试查询开始 ===");
        log.info("问题: {}", question);

        java.util.Map<String, Object> debugInfo = new java.util.LinkedHashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            // 1. 记录当前线程信息
            Thread currentThread = Thread.currentThread();
            debugInfo.put("threadName", currentThread.getName());
            debugInfo.put("isVirtualThread", currentThread.isVirtual() ? "✓ YES" : "✗ NO");
            debugInfo.put("threadId", currentThread.threadId());
            VirtualThreadInspector.logThreadInfo("RAG测试查询", "问题: " + question.substring(0, Math.min(30, question.length())));

            // 2. 手动执行向量检索（验证检索功能）
            log.info("执行向量检索...");
            long retrievalStart = System.currentTimeMillis();
            List<Document> retrievedDocs = vectorStoreService.searchSimilarDocuments(question, 5, 0.7);
            long retrievalDuration = System.currentTimeMillis() - retrievalStart;

            log.info("检索完成: 找到 {} 条相关文档，耗时: {} ms", retrievedDocs.size(), retrievalDuration);
            debugInfo.put("retrievedDocsCount", retrievedDocs.size());
            debugInfo.put("retrievalDuration", retrievalDuration + " ms");

            // 3. 记录检索到的文档片段
            List<java.util.Map<String, Object>> docDetails = new java.util.ArrayList<>();
            for (int i = 0; i < retrievedDocs.size(); i++) {
                Document doc = retrievedDocs.get(i);
                java.util.Map<String, Object> docInfo = new java.util.LinkedHashMap<>();
                docInfo.put("index", i + 1);
                docInfo.put("content", doc.getContent().substring(0, Math.min(200, doc.getContent().length())) + "...");
                docInfo.put("metadata", doc.getMetadata());
                docDetails.add(docInfo);

                log.info("文档 #{}: {}", i + 1, doc.getContent().substring(0, Math.min(100, doc.getContent().length())));
            }
            debugInfo.put("retrievedDocs", docDetails);

            // 4. 调用 ChatClient（Advisor 自动执行）
            log.info("调用 ChatClient 生成答案...");
            long chatStart = System.currentTimeMillis();
            String answer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();
            long chatDuration = System.currentTimeMillis() - chatStart;

            log.info("答案生成完成，耗时: {} ms", chatDuration);
            debugInfo.put("answer", answer);
            debugInfo.put("answerLength", answer.length());
            debugInfo.put("chatDuration", chatDuration + " ms");

            // 5. 总结
            long totalDuration = System.currentTimeMillis() - startTime;
            debugInfo.put("totalQueryDuration", totalDuration + " ms");

            log.info("=== RAG 测试查询完成 ===");
            log.info("总耗时: {} ms (检索:  ms, 生成: {} ms)", totalDuration, retrievalDuration, chatDuration);

            return debugInfo;

        } catch (Exception e) {
            log.error("RAG 测试查询失败", e);
            debugInfo.put("error", e.getMessage());
            throw new RuntimeException("RAG 测试查询失败: " + e.getMessage(), e);
        }
    }
}
