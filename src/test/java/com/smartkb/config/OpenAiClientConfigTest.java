package com.smartkb.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientConfigTest {

    @Test
    void shouldOmitMaxTokensWhenNoExplicitLimitIsConfigured() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, requestBody));
        server.start();

        try {
            OpenAiClientConfig config = new OpenAiClientConfig();
            ReflectionTestUtils.setField(config, "baseUrl", "http://localhost:" + server.getAddress().getPort());
            ReflectionTestUtils.setField(config, "apiKey", "test-key");
            ReflectionTestUtils.setField(config, "model", "deepseek-v4-flash");
            ReflectionTestUtils.setField(config, "temperature", 0.7F);
            ReflectionTestUtils.setField(config, "maxTokens", 0);

            String answer = config.openAiChatModel(config.openAiApi())
                    .call(new Prompt("正式测试问题"))
                    .getResult().getOutput().getContent();

            JsonNode payload = new ObjectMapper().readTree(requestBody.get());
            List<String> requestFields = new ArrayList<>();
            payload.fieldNames().forEachRemaining(requestFields::add);
            assertThat(answer).isEqualTo("ok");
            assertThat(payload.path("model").asText()).isEqualTo("deepseek-v4-flash");
            assertThat(requestFields).containsExactlyInAnyOrder("messages", "model", "stream", "temperature");
            assertThat(payload.path("stream").asBoolean()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    private void respond(HttpExchange exchange, AtomicReference<String> requestBody) throws java.io.IOException {
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = """
                {"id":"chatcmpl-test","object":"chat.completion","created":0,"model":"deepseek-v4-flash",
                "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
                "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
