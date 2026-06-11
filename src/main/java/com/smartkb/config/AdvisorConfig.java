package com.smartkb.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Advisor 配置类
 * <p>
 * 核心功能：
 * 1. 配置 ChatClient（Spring AI 统一对话客户端）
 * 2. 配置 Advisor 链（RAG 核心组件）：
 *    - QuestionAnswerAdvisor：基于检索文档生成答案
 *    - RetrievalAugmentationAdvisor：自动向量检索并注入 Prompt
 *    - ChatMemory：多轮对话记忆（Redis 或 InMemory）
 * <p>
 * 技术选型理由：
 * - Spring AI Advisor 体系：2026 年企业 RAG 落地主流方案
 * - 优势：声明式配置，无需手动编排 RAG 流程（检索 → 注入 → 生成）
 * - 可扩展性：后续可添加 QueryRewritingAdvisor、MetadataFilterAdvisor 等
 * <p>
 * 与 RagService 的配合：
 * - RagService 调用 ChatClient.prompt().call()
 * - Advisor 链自动执行：检索 → 注入上下文 → 生成答案
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdvisorConfig {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    @Value("${smartkb.rag.top-k:5}")
    private int topK;

    @Value("${smartkb.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    /**
     * 配置 ChatMemory（多轮对话记忆）
     * <p>
     * 当前使用 InMemoryChatMemory（开发环境）
     * 生产环境建议使用 Redis：
     * - new RedisChatMemory(redisTemplate)
     * - 支持分布式会话共享
     */
    @Bean
    public ChatMemory chatMemory() {
        log.info("初始化 ChatMemory (InMemory 模式)");
        return new InMemoryChatMemory();
    }

    /**
     * 配置 ChatClient（Spring AI 统一对话客户端）
     * <p>
     * Advisor 链说明：
     * 1. QuestionAnswerAdvisor：
     *    - 核心 RAG Advisor，自动执行向量检索
     *    - 将检索到的文档注入到 System Message
     *    - LLM 基于注入的文档生成答案
     * <p>
     * 执行顺序：
     * User Question → QuestionAnswerAdvisor (检索+注入) → ChatModel → Answer
     */
    @Bean
    public ChatClient chatClient() {
        log.info("初始化 ChatClient with Advisor 链");
        log.info("RAG 配置 - topK: {}, similarityThreshold: {}", topK, similarityThreshold);

        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        // QuestionAnswerAdvisor - RAG 核心组件
                        new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()
                                .withTopK(topK)
                                .withSimilarityThreshold(similarityThreshold))
                )
                .build();
    }
}
