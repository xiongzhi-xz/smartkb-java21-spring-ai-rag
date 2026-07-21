package com.smartkb.infrastructure.persistence;

import com.smartkb.domain.conversation.ConversationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcConversationRepositoryTest {

    @Test
    void shouldAdvanceSequenceBeforePersistingEachMessage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("conv-1")))
                .thenReturn(1L, 2L);
        JdbcConversationRepository repository = new JdbcConversationRepository(jdbcTemplate);

        repository.append("conv-1", List.of(
                new ConversationMessage("USER", "question"),
                new ConversationMessage("ASSISTANT", "answer")
        ));

        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(Long.class), eq("conv-1"));
        verify(jdbcTemplate).update(anyString(), any(), eq("conv-1"), eq(1L), eq("USER"), eq("question"));
        verify(jdbcTemplate).update(anyString(), any(), eq("conv-1"), eq(2L), eq("ASSISTANT"), eq("answer"));
    }
}
