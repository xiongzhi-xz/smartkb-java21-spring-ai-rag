package com.smartkb.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

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

    @Value("${spring.ai.openai.chat.options.max-tokens:0}")
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

        // The transit endpoint returns incomplete bodies through Apache HttpComponents.
        // Use the JDK URLConnection implementation, which is verified from the runtime container.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    log.debug("OpenAI request: uri={}, fields={}, bodyBytes={}",
                            request.getURI(), requestFields(body), body.length);
                    return execution.execute(request, body);
                });
        return new OpenAiApi(baseUrl, apiKey, restClientBuilder, WebClient.builder());
    }

    /**
     * 手动创建 OpenAI Chat Model
     */
    @Bean
    @Primary
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        log.info("创建自定义 OpenAI Chat Model");

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature);
        if (maxTokens != null && maxTokens > 0) {
            options.withMaxTokens(maxTokens);
        }

        return new OpenAiChatModel(openAiApi, options.build());
    }

    private List<String> requestFields(byte[] body) {
        try {
            JsonNode payload = new ObjectMapper().readTree(body);
            List<String> fields = new ArrayList<>();
            payload.fieldNames().forEachRemaining(fields::add);
            return fields;
        } catch (Exception exception) {
            return List.of("unparseable");
        }
    }
}
