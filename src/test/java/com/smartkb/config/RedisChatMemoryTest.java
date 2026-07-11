package com.smartkb.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class RedisChatMemoryTest {

    private static final String KEY = "smartkb:chat:conv-1";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ListOperations<String, String> listOperations = mock(ListOperations.class);
    private final RedisChatMemory memory = new RedisChatMemory(redisTemplate, 2);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Test
    void shouldAppendMessagesAndRefreshTtl() {
        memory.add("conv-1", List.of(
                new UserMessage("hello \"SmartKB\"\nline"),
                new AssistantMessage("answer")
        ));

        ArgumentCaptor<Collection> valuesCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(listOperations).rightPushAll(eq(KEY), valuesCaptor.capture());
        verify(redisTemplate).expire(KEY, 2, TimeUnit.HOURS);

        List<String> values = valuesCaptor.getValue().stream()
                .map(String.class::cast)
                .toList();
        assertEquals(2, values.size());
        assertEquals("{\"type\":\"USER\",\"content\":\"hello \\\"SmartKB\\\"\\nline\"}", values.getFirst());
        assertEquals("{\"type\":\"ASSISTANT\",\"content\":\"answer\"}", values.get(1));
    }

    @Test
    void shouldTrimOldMessagesWhenConversationIsTooLong() {
        when(listOperations.size(KEY)).thenReturn(101L, 100L);

        memory.add("conv-1", new UserMessage("latest"));

        verify(listOperations).trim(KEY, 1, -1);
        verify(redisTemplate).expire(KEY, 2, TimeUnit.HOURS);
    }

    @Test
    void shouldReadLatestMessagesInOrderAndRefreshTtl() {
        when(listOperations.size(KEY)).thenReturn(3L);
        when(listOperations.range(KEY, 1, -1)).thenReturn(List.of(
                "{\"type\":\"ASSISTANT\",\"content\":\"previous\"}",
                "{\"type\":\"USER\",\"content\":\"follow\\nup\"}"
        ));

        List<Message> messages = memory.get("conv-1", 2);

        assertEquals(2, messages.size());
        assertInstanceOf(AssistantMessage.class, messages.getFirst());
        assertEquals("previous", messages.getFirst().getContent());
        assertInstanceOf(UserMessage.class, messages.get(1));
        assertEquals("follow\nup", messages.get(1).getContent());
        verify(redisTemplate).expire(KEY, 2, TimeUnit.HOURS);
    }

    @Test
    void shouldClearConversationKey() {
        memory.clear("conv-1");

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void shouldIgnoreBlankConversationId() {
        memory.add("", new UserMessage("ignored"));
        List<Message> messages = memory.get("", 10);
        memory.clear("");

        assertEquals(List.of(), messages);
        verify(listOperations, never()).rightPushAll(any(), any(Collection.class));
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }
}
