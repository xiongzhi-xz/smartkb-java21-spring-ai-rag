package com.smartkb.config;

import com.smartkb.infrastructure.persistence.PostgresChatMemory;
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
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring AI 客户端与 Advisor 配置。
 *
 * <p>会话事实通过 PostgreSQL {@link PostgresChatMemory} 持久化，Redis 不再作为 ChatMemory
 * 后端。当前向量检索仍沿用兼容适配器，待 Milvus 与 OpenSearch 适配层完成后独立切换。</p>
 */
@Slf4j
@Configuration
public class AdvisorConfig {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public AdvisorConfig(ChatModel chatModel, VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Value("${smartkb.rag.top-k:5}")
    private int topK;

    @Value("${smartkb.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    @Bean
    public ChatMemory chatMemory() {
        log.info("初始化 ChatMemory (PostgreSQL 持久化模式)");
        return new PostgresChatMemory(jdbcTemplate);
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()
                                .withTopK(topK)
                                .withSimilarityThreshold(similarityThreshold))
                )
                .build();
    }

    @Bean("conversationChatClient")
    public ChatClient conversationChatClient(ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }
}
