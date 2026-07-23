package com.smartkb.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.domain.conversation.ConversationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisConversationContextCacheTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisConversationContextCache cache =
            new RedisConversationContextCache(redisTemplate, new ObjectMapper());

    @BeforeEach
    void setUp() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Field ttl = RedisConversationContextCache.class.getDeclaredField("ttl");
        ttl.setAccessible(true);
        ttl.set(cache, Duration.ofMinutes(5));
    }

    @Test
    void shouldReadSerializedContext() throws Exception {
        String value = new ObjectMapper().writeValueAsString(List.of(
                new ConversationMessage("USER", "question"),
                new ConversationMessage("ASSISTANT", "answer")
        ));
        when(valueOperations.get("smartkb:conversation:context:conv-1")).thenReturn(value);

        Optional<List<ConversationMessage>> result = cache.get("conv-1");

        assertTrue(result.isPresent());
        assertEquals(List.of(
                new ConversationMessage("USER", "question"),
                new ConversationMessage("ASSISTANT", "answer")
        ), result.get());
    }

    @Test
    void shouldWriteContextWithTtl() throws Exception {
        List<ConversationMessage> messages = List.of(new ConversationMessage("USER", "question"));

        cache.put("conv-1", messages);

        verify(valueOperations).set(
                eq("smartkb:conversation:context:conv-1"),
                eq(new ObjectMapper().writeValueAsString(messages)),
                eq(Duration.ofMinutes(5))
        );
    }

    @Test
    void shouldEvictContext() {
        cache.evict("conv-1");

        verify(redisTemplate).delete("smartkb:conversation:context:conv-1");
    }

    @Test
    void shouldTreatRedisReadFailureAsCacheMiss() {
        when(valueOperations.get("smartkb:conversation:context:conv-1"))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertEquals(Optional.empty(), cache.get("conv-1"));
    }

    @Test
    void shouldIgnoreRedisWriteAndEvictionFailures() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(valueOperations).set(eq("smartkb:conversation:context:conv-1"), eq("[]"), eq(Duration.ofMinutes(5)));
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redisTemplate).delete("smartkb:conversation:context:conv-1");

        cache.put("conv-1", List.of());
        cache.evict("conv-1");
    }
}
