package com.smartkb.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresChatMemoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PostgresChatMemory memory = new PostgresChatMemory(jdbcTemplate);

    @Test
    void shouldPersistMessagesWithMonotonicSequence() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("conv-1")))
                .thenReturn(1L, 2L);

        memory.add("conv-1", List.of(new UserMessage("question"), new AssistantMessage("answer")));

        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(Long.class), eq("conv-1"));
        verify(jdbcTemplate).update(anyString(), any(), eq("conv-1"), eq(1L), eq("USER"), eq("question"));
        verify(jdbcTemplate).update(anyString(), any(), eq("conv-1"), eq(2L), eq("ASSISTANT"), eq("answer"));
    }

    @Test
    void shouldLoadRecentMessagesInChronologicalOrder() {
        when(jdbcTemplate.queryForList(anyString(), eq("conv-1"), eq(3))).thenReturn(List.of(
                Map.of("message_type", "USER", "content", "first"),
                Map.of("message_type", "SYSTEM", "content", "policy"),
                Map.of("message_type", "ASSISTANT", "content", "last")
        ));

        List<Message> messages = memory.get("conv-1", 3);

        assertEquals(3, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertInstanceOf(SystemMessage.class, messages.get(1));
        assertInstanceOf(AssistantMessage.class, messages.get(2));
        assertEquals("last", messages.get(2).getContent());
    }

    @Test
    void shouldClearMessagesAndMarkConversationCleared() {
        memory.clear("conv-1");

        verify(jdbcTemplate, times(2)).update(anyString(), eq("conv-1"));
    }

    @Test
    void shouldIgnoreBlankConversationId() {
        memory.add("", new UserMessage("ignored"));
        memory.clear(" ");

        assertEquals(List.of(), memory.get("", 10));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }
}
