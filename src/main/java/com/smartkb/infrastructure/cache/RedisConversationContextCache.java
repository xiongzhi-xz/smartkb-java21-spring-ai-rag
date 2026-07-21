package com.smartkb.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.application.port.outbound.ConversationContextCache;
import com.smartkb.domain.conversation.ConversationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Redis 会话上下文缓存。Redis 异常只导致缓存未命中，不影响 PostgreSQL 会话事实。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisConversationContextCache implements ConversationContextCache {

    private static final String KEY_PREFIX = "smartkb:conversation:context:";
    private static final TypeReference<List<ConversationMessage>> MESSAGE_LIST = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${smartkb.conversation-cache.ttl:5m}")
    private Duration ttl;

    @Override
    public Optional<List<ConversationMessage>> get(String conversationId) {
        try {
            String value = redisTemplate.opsForValue().get(key(conversationId));
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, MESSAGE_LIST));
        } catch (Exception exception) {
            log.warn("会话上下文缓存读取失败，回退 PostgreSQL: conversationId={}", conversationId, exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(String conversationId, List<ConversationMessage> messages) {
        try {
            redisTemplate.opsForValue().set(key(conversationId), objectMapper.writeValueAsString(messages), ttl);
        } catch (Exception exception) {
            log.warn("会话上下文缓存写入失败，忽略缓存: conversationId={}", conversationId, exception);
        }
    }

    @Override
    public void evict(String conversationId) {
        try {
            redisTemplate.delete(key(conversationId));
        } catch (Exception exception) {
            log.warn("会话上下文缓存失效失败，等待 TTL 清理: conversationId={}", conversationId, exception);
        }
    }

    private String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }
}
