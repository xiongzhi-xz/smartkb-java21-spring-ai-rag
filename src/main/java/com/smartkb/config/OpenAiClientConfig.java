package com.smartkb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 自定义 OpenAI 客户端配置
 * <p>
 * 目的：绕过 Spring AI 的 auto-configuration，手动创建 OpenAI 客户端
 * 解决问题：auto-configuration 无法正确加载中转站 base-url
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class OpenAiClientConfig {

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Float temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:2000}")
    private Integer maxTokens;

    /**
     * 手动创建 OpenAI API 客户端
     */
    @Bean
    @Primary
    public OpenAiApi openAiApi() {
        log.info("创建自定义 OpenAI API 客户端");
        log.info("Base URL: {}", baseUrl);
        log.info("Model: {}", model);

        return new OpenAiApi(baseUrl, apiKey);
    }

    /**
     * 手动创建 OpenAI Chat Model
     */
    @Bean
    @Primary
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        log.info("创建自定义 OpenAI Chat Model");

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(maxTokens)
                .build();

        return new OpenAiChatModel(openAiApi, options);
    }
}
