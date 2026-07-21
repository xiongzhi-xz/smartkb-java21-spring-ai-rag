package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.ConversationRepository;
import com.smartkb.application.port.outbound.ConversationContextCache;
import com.smartkb.domain.conversation.ConversationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresChatMemoryTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ConversationContextCache contextCache = mock(ConversationContextCache.class);
    private final PostgresChatMemory memory = new PostgresChatMemory(conversationRepository, contextCache, 100);

    @Test
    void shouldPersistMessagesWithMonotonicSequence() {
        memory.add("conv-1", List.of(new UserMessage("question"), new AssistantMessage("answer")));

        verify(conversationRepository).append("conv-1", List.of(
                new ConversationMessage("USER", "question"),
                new ConversationMessage("ASSISTANT", "answer")
        ));
        verify(contextCache).evict("conv-1");
    }

    @Test
    void shouldLoadRecentMessagesInChronologicalOrder() {
        when(contextCache.get("conv-1")).thenReturn(Optional.empty());
        when(conversationRepository.findRecent("conv-1", 100)).thenReturn(List.of(
                new ConversationMessage("USER", "first"),
                new ConversationMessage("SYSTEM", "policy"),
                new ConversationMessage("ASSISTANT", "last")
        ));

        List<Message> messages = memory.get("conv-1", 3);

        assertEquals(3, messages.size());
        assertInstanceOf(UserMessage.class, messages.get(0));
        assertInstanceOf(SystemMessage.class, messages.get(1));
        assertInstanceOf(AssistantMessage.class, messages.get(2));
        assertEquals("last", messages.get(2).getContent());
        verify(contextCache).put("conv-1", List.of(
                new ConversationMessage("USER", "first"),
                new ConversationMessage("SYSTEM", "policy"),
                new ConversationMessage("ASSISTANT", "last")
        ));
    }

    @Test
    void shouldReadFromCacheBeforeRepository() {
        when(contextCache.get("conv-1")).thenReturn(Optional.of(List.of(
                new ConversationMessage("USER", "cached")
        )));

        List<Message> messages = memory.get("conv-1", 1);

        assertEquals("cached", messages.getFirst().getContent());
        verify(conversationRepository, never()).findRecent("conv-1", 100);
    }

    @Test
    void shouldClearMessagesAndMarkConversationCleared() {
        memory.clear("conv-1");

        verify(conversationRepository).clearMessages("conv-1");
        verify(contextCache).evict("conv-1");
    }

    @Test
    void shouldIgnoreBlankConversationId() {
        memory.add("", new UserMessage("ignored"));
        memory.clear(" ");

        assertEquals(List.of(), memory.get("", 10));
        verify(conversationRepository, never()).append(eq(""), org.mockito.ArgumentMatchers.anyList());
    }
}
