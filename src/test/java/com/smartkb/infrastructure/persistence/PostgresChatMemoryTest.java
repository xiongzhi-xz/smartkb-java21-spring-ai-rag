package com.smartkb.infrastructure.persistence;

import com.smartkb.application.port.outbound.ConversationRepository;
import com.smartkb.domain.conversation.ConversationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresChatMemoryTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final PostgresChatMemory memory = new PostgresChatMemory(conversationRepository);

    @Test
    void shouldPersistMessagesWithMonotonicSequence() {
        memory.add("conv-1", List.of(new UserMessage("question"), new AssistantMessage("answer")));

        verify(conversationRepository).append("conv-1", List.of(
                new ConversationMessage("USER", "question"),
                new ConversationMessage("ASSISTANT", "answer")
        ));
    }

    @Test
    void shouldLoadRecentMessagesInChronologicalOrder() {
        when(conversationRepository.findRecent("conv-1", 3)).thenReturn(List.of(
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
    }

    @Test
    void shouldClearMessagesAndMarkConversationCleared() {
        memory.clear("conv-1");

        verify(conversationRepository).clearMessages("conv-1");
    }

    @Test
    void shouldIgnoreBlankConversationId() {
        memory.add("", new UserMessage("ignored"));
        memory.clear(" ");

        assertEquals(List.of(), memory.get("", 10));
        verify(conversationRepository, never()).append(eq(""), org.mockito.ArgumentMatchers.anyList());
    }
}
