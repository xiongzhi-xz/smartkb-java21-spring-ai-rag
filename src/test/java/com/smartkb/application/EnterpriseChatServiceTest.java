package com.smartkb.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.application.port.outbound.ConversationRepository;
import com.smartkb.application.port.outbound.RetrievalTraceRepository;
import com.smartkb.domain.*;
import com.smartkb.service.QueryRewritingService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnterpriseChatServiceTest {
    @Test
    void shouldPersistAnswerCitationsAndTraceAfterEnterpriseRetrieval() {
        EnterpriseRetrievalService retrieval = mock(EnterpriseRetrievalService.class);
        QueryRewritingService rewriting = mock(QueryRewritingService.class);
        ChatModel model = mock(ChatModel.class);
        ConversationRepository conversations = mock(ConversationRepository.class);
        RetrievalTraceRepository traces = mock(RetrievalTraceRepository.class);
        UUID kb = UUID.randomUUID();
        RetrievalCandidate candidate = new RetrievalCandidate(UUID.randomUUID(), UUID.randomUUID(), kb, 1, 0, "evidence", 1);
        when(rewriting.rewriteQuery("question")).thenReturn("rewritten");
        when(retrieval.retrieve(any())).thenReturn(new EnterpriseRetrievalResult("hybrid",
                List.of(new FusedRetrievalCandidate(candidate, 1, 1, 0.1)), List.of()));
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation("answer"))));
        when(conversations.appendWithMetadata(anyString(), any())).thenReturn(UUID.randomUUID());

        EnterpriseChatResult result = new EnterpriseChatService(retrieval, rewriting, model, conversations, traces,
                new ObjectMapper()).answer("question", "conv", kb, List.of(), ignored -> { });

        assertThat(result.answer()).isEqualTo("answer");
        assertThat(result.references()).hasSize(1);
        verify(conversations, times(2)).appendWithMetadata(eq("conv"), any());
        verify(traces).save(any(), any(), eq("rewritten"), contains(candidate.chunkId().toString()),
                eq("hybrid"), anyLong());
    }
}
