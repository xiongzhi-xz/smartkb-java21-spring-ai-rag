package com.smartkb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

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
        long startTime = System.currentTimeMillis();

        try {
            // 1. 解析并切片文档
            logThreadInfo("文档解析开始");
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
            logThreadInfo("Embedding 生成开始");
            embeddingService.embedDocumentsBatch(chunks);
            log.info("Embedding 生成完成");

            // 4. 存入向量库
            logThreadInfo("向量存储开始");
            vectorStoreService.addDocuments(chunks);

            long duration = System.currentTimeMillis() - startTime;
            log.info("文档处理完成: {}, {} chunks, 耗时: {} ms",
                    resource.getFilename(), chunks.size(), duration);

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
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 为每个文档创建一个虚拟线程并发处理
            List<java.util.concurrent.Future<Integer>> futures = resources.stream()
                    .map(resource -> executor.submit(() -> addDocument(resource, fileType, null)))
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
        logThreadInfo("RAG 查询开始");

        try {
            // ChatClient 已配置 Advisor 链，会自动执行 RAG 流程
            String answer = chatClient.prompt()
                    .user(question)
                    .call()
                    .content();

            log.info("RAG 问答完成");
            return answer;

        } catch (Exception e) {
            log.error("RAG 问答失败: {}", question, e);
            throw new RuntimeException("RAG 问答失败: " + e.getMessage(), e);
        }
    }

    /**
     * RAG 问答（多轮对话，支持上下文记忆）
     * <p>
     * 使用 VectorStoreChatMemoryAdvisor 自动管理对话历史：
     * - 历史消息存储在 Redis（基于 conversationId）
     * - Advisor 会自动注入历史上下文到 Prompt
     *
     * @param question       用户问题
     * @param conversationId 会话 ID（用于区分不同用户/会话）
     * @return 答案
     */
    public String queryWithContext(String question, String conversationId) {
        log.info("多轮 RAG 问答: conversationId={}, question={}",
                conversationId, question.substring(0, Math.min(50, question.length())));
        logThreadInfo("多轮 RAG 查询开始");

        try {
            // ChatClient 的 VectorStoreChatMemoryAdvisor 会自动加载历史
            String answer = chatClient.prompt()
                    .user(question)
                    .advisors(a -> a.param("conversationId", conversationId))  // 传递会话 ID
                    .call()
                    .content();

            log.info("多轮 RAG 问答完成: conversationId={}", conversationId);
            return answer;

        } catch (Exception e) {
            log.error("多轮 RAG 问答失败: conversationId={}, question={}", conversationId, question, e);
            throw new RuntimeException("多轮 RAG 问答失败: " + e.getMessage(), e);
        }
    }

    /**
     * 记录当前线程信息（用于验证 Virtual Threads 是否生效）
     */
    private void logThreadInfo(String context) {
        Thread thread = Thread.currentThread();
        log.debug("[{}] 线程信息 - 名称: {}, 是否虚拟线程: {}, ID: {}",
                context, thread.getName(), thread.isVirtual(), thread.threadId());
    }
}
