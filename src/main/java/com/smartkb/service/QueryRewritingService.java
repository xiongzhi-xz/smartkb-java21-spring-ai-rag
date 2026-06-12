package com.smartkb.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 查询改写服务（Advanced RAG 核心组件）
 * <p>
 * 核心功能：
 * 1. 将用户的口语化问题改写为更适合向量检索的查询
 * 2. 扩展查询关键词（增加同义词、相关术语）
 * 3. 消除歧义（明确指代、补充上下文）
 * <p>
 * 使用场景：
 * - 用户问："它有什么优势？" → 改写为："Virtual Threads 有什么优势？"
 * - 用户问："怎么用？" → 改写为："如何在 Spring Boot 中使用 Virtual Threads？"
 * <p>
 * 技术选型理由：
 * - 使用 LLM 进行智能改写（而非规则匹配）
 * - 2026 年 Advanced RAG 标准做法
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewritingService {

    private final ChatModel chatModel;

    private static final String REWRITE_PROMPT_TEMPLATE = """
            你是一个查询优化专家。用户的原始问题可能不够明确，请将其改写为更适合知识库检索的查询。

            改写规则：
            1. 补充缺失的上下文（如果用户说"它"、"这个"等指代词，尝试从对话历史推断）
            2. 扩展关键词（添加同义词、相关技术术语）
            3. 明确技术领域（例如：Java、Spring、AI 等）
            4. 保持简洁（不要过度扩展）

            原始问题：{question}
            对话历史：{history}

            改写后的查询（只返回改写结果，不要解释）：
            """;

    /**
     * 改写用户查询
     *
     * @param originalQuery 原始查询
     * @param history       对话历史（可选，用于上下文推断）
     * @return 改写后的查询
     */
    public String rewriteQuery(String originalQuery, String history) {
        log.info("开始查询改写: {}", originalQuery);

        try {
            PromptTemplate promptTemplate = new PromptTemplate(REWRITE_PROMPT_TEMPLATE);
            Prompt prompt = promptTemplate.create(Map.of(
                    "question", originalQuery,
                    "history", history != null ? history : "无"
            ));

            String rewrittenQuery = chatModel.call(prompt).getResult().getOutput().getContent();
            rewrittenQuery = rewrittenQuery.trim();

            log.info("查询改写完成: {} → {}", originalQuery, rewrittenQuery);
            return rewrittenQuery;

        } catch (Exception e) {
            log.error("查询改写失败，返回原始查询: {}", originalQuery, e);
            return originalQuery;  // 失败时降级为原始查询
        }
    }

    /**
     * 改写用户查询（无历史上下文）
     *
     * @param originalQuery 原始查询
     * @return 改写后的查询
     */
    public String rewriteQuery(String originalQuery) {
        return rewriteQuery(originalQuery, null);
    }
}
