package com.smartkb.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisChatMemoryIT {

    private static final String CONVERSATION_ID = "redis-it-conversation";
    private static final String KEY = "smartkb:chat:" + CONVERSATION_ID;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisChatMemory memory;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration redisConfiguration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory = new LettuceConnectionFactory(redisConfiguration);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.delete(KEY);

        memory = new RedisChatMemory(redisTemplate, 1);
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            redisTemplate.delete(KEY);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldPersistReadRefreshTtlAndClearConversationInRedis() {
        memory.add(CONVERSATION_ID, List.of(
                new UserMessage("hello redis"),
                new AssistantMessage("hello SmartKB")
        ));

        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(KEY)));
        assertEquals(2L, redisTemplate.opsForList().size(KEY));

        Long ttlSeconds = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);
        assertNotNull(ttlSeconds);
        assertTrue(ttlSeconds > 0 && ttlSeconds <= TimeUnit.HOURS.toSeconds(1));

        List<Message> messages = memory.get(CONVERSATION_ID, 2);

        assertEquals(2, messages.size());
        assertInstanceOf(UserMessage.class, messages.getFirst());
        assertEquals("hello redis", messages.getFirst().getContent());
        assertInstanceOf(AssistantMessage.class, messages.get(1));
        assertEquals("hello SmartKB", messages.get(1).getContent());

        Long refreshedTtlSeconds = redisTemplate.getExpire(KEY, TimeUnit.SECONDS);
        assertNotNull(refreshedTtlSeconds);
        assertTrue(refreshedTtlSeconds > 0);

        memory.clear(CONVERSATION_ID);

        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(KEY)));
    }
}
