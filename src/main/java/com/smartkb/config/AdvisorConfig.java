package com.smartkb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Spring AI Advisor 配置类
 * <p>
 * 核心功能：
 * 1. 配置 ChatClient（Spring AI 统一对话客户端）
 * 2. 配置 Advisor 链（RAG 核心组件）：
 *    - QuestionAnswerAdvisor：基于检索文档生成答案
 *    - MessageChatMemoryAdvisor：多轮对话记忆（Redis 持久化）
 * <p>
 * 技术选型理由：
 * - Spring AI Advisor 体系：2026 年企业 RAG 落地主流方案
 * - 优势：声明式配置，无需手动编排 RAG 流程（检索 → 注入 → 生成）
 * - 可扩展性：后续可添加 QueryRewritingAdvisor、MetadataFilterAdvisor 等
 * <p>
 * ChatMemory 存储方案（Redis vs InMemory）：
 * - Redis：服务重启后会话不丢失、支持分布式共享、TTL 自动过期
 * - InMemory：仅开发调试用，JVM 重启即丢失
 * <p>
 * 面试讲法要点：
 * - Advisor 链的执行顺序：MessageChatMemoryAdvisor 在 QuestionAnswerAdvisor 之前，
 *   确保历史消息先注入 Prompt，再叠加 RAG 检索结果，让 LLM 同时拥有对话上下文和文档知识。
 * - ChatMemory 接口只有 add/get/clear 三个方法，非常轻量；
 *   我们自研 RedisChatMemory 实现了 Redis List + TTL 的存储方案，
 *   对标 Spring AI 官方 1.0 正式的 RedisChatMemory（M1 版本尚未提供）。
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class AdvisorConfig {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final StringRedisTemplate stringRedisTemplate;

    public AdvisorConfig(ChatModel chatModel, VectorStore vectorStore,
                         StringRedisTemplate stringRedisTemplate) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Value("${smartkb.rag.top-k:5}")
    private int topK;

    @Value("${smartkb.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    /** 会话过期时间（小时），默认 24 小时 */
    @Value("${smartkb.chat-memory.ttl-hours:24}")
    private long chatMemoryTtlHours;

    /**
     * 配置 ChatMemory（多轮对话记忆，Redis 持久化）
     * <p>
     * 存储结构：
     * - Key：smartkb:chat:{conversationId}
     * - Value：Redis List，每个元素是一条消息的 JSON
     * - TTL：默认 24 小时，活跃会话自动续期
     * <p>
     * 容错设计：
     * - Redis 不可用时降级为 InMemoryChatMemory，不影响对话功能
     * - 降级通过 try-catch 捕获连接异常来实现
     */
    @Bean
    public ChatMemory chatMemory() {
        try {
            // 测试 Redis 连接是否可用
            var connectionFactory = stringRedisTemplate.getConnectionFactory();
            if (connectionFactory != null) {
                try (var conn = connectionFactory.getConnection()) {
                    conn.ping();
                }
            }

            ChatMemory redisMemory = new RedisChatMemory(stringRedisTemplate, chatMemoryTtlHours);
            log.info("初始化 ChatMemory (Redis 模式, TTL={}h)", chatMemoryTtlHours);
            return redisMemory;
        } catch (Exception e) {
            // Redis 不可用时降级为 InMemory
            log.warn("Redis 连接失败，降级为 InMemoryChatMemory: {}", e.getMessage());
            return new org.springframework.ai.chat.memory.InMemoryChatMemory();
        }
    }

    /**
     * 配置 ChatClient（Spring AI 统一对话客户端）
     * <p>
     * Advisor 链说明：
     * 1. MessageChatMemoryAdvisor：
     *    - 多轮对话记忆，自动加载/存储历史消息
     *    - 通过 conversationId 隔离不同会话
     * <p>
     * 2. QuestionAnswerAdvisor：
     *    - 核心 RAG Advisor，自动执行向量检索
     *    - 将检索到的文档注入到 System Message
     *    - LLM 基于注入的文档生成答案
     * <p>
     * 执行顺序：
     * User Question → MessageChatMemoryAdvisor（注入历史）→ QuestionAnswerAdvisor（检索+注入）→ ChatModel → Answer
     */
    @Bean
    public ChatClient chatClient(ChatMemory chatMemory) {
        log.info("初始化 ChatClient with Advisor 链");
        log.info("RAG 配置 - topK: {}, similarityThreshold: {}", topK, similarityThreshold);

        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        // MessageChatMemoryAdvisor - 多轮对话记忆（Redis 持久化）
                        new MessageChatMemoryAdvisor(chatMemory),

                        // QuestionAnswerAdvisor - RAG 核心组件
                        new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()
                                .withTopK(topK)
                                .withSimilarityThreshold(similarityThreshold))
                )
                .build();
    }
}
