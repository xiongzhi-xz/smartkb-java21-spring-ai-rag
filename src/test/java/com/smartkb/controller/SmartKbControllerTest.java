package com.smartkb.controller;

import com.smartkb.config.GlobalExceptionHandler;
import com.smartkb.domain.AdvancedRagMetrics;
import com.smartkb.domain.AdvancedRagResult;
import com.smartkb.service.AdvancedRagService;
import com.smartkb.service.DocumentManagementService;
import com.smartkb.service.RagService;
import com.smartkb.service.SmartKbMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SmartKbController.class)
@Import(GlobalExceptionHandler.class)
class SmartKbControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagService ragService;

    @MockBean
    private AdvancedRagService advancedRagService;

    @MockBean
    private DocumentManagementService documentManagementService;

    @MockBean
    private ChatMemory chatMemory;

    @MockBean
    private SmartKbMetricsService metricsService;

    @Test
    void shouldUseProvidedConversationIdForConversationChat() throws Exception {
        when(ragService.queryWithContext("What is RAG?", "conv-1")).thenReturn("RAG answer");

        mockMvc.perform(post("/api/chat/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is RAG?",
                                  "conversationId": "conv-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.answer").value("RAG answer"))
                .andExpect(jsonPath("$.content").value("RAG answer"))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));

        verify(metricsService).recordConversationRequest();
        verify(ragService).queryWithContext("What is RAG?", "conv-1");
    }

    @Test
    void shouldGenerateConversationIdWhenConversationChatDoesNotProvideOne() throws Exception {
        when(ragService.queryWithContext(eq("What is RAG?"), anyString())).thenReturn("RAG answer");

        mockMvc.perform(post("/api/chat/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is RAG?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.conversationId").isNotEmpty());

        var conversationId = forClass(String.class);
        verify(ragService).queryWithContext(eq("What is RAG?"), conversationId.capture());
        assertThat(conversationId.getValue()).isNotBlank();
    }

    @Test
    void shouldUseProvidedConversationIdForAdvancedChat() throws Exception {
        var result = new AdvancedRagResult(
                "Advanced answer",
                "rewritten question",
                List.of("demo.md"),
                List.of(),
                1,
                new AdvancedRagMetrics(1, 2, 3, 4, 5, 15)
        );
        Map<String, Object> metadataFilter = Map.of("fileName", "demo.md");
        when(advancedRagService.queryAdvancedWithDetails("Explain RAG", metadataFilter, "conv-advanced"))
                .thenReturn(result);

        mockMvc.perform(post("/api/chat/advanced")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Explain RAG",
                                  "conversationId": "conv-advanced",
                                  "metadataFilter": {
                                    "fileName": "demo.md"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.answer").value("Advanced answer"))
                .andExpect(jsonPath("$.conversationId").value("conv-advanced"))
                .andExpect(jsonPath("$.rewrittenQuery").value("rewritten question"))
                .andExpect(jsonPath("$.retrievedCount").value(1));

        verify(advancedRagService).queryAdvancedWithDetails("Explain RAG", metadataFilter, "conv-advanced");
    }

    @Test
    void shouldClearChatMemory() throws Exception {
        mockMvc.perform(delete("/api/chat/memory/conv-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.conversationId").value("conv-1"));

        verify(chatMemory).clear("conv-1");
    }

    @Test
    void shouldReturnErrorWhenClearChatMemoryFails() throws Exception {
        doThrow(new IllegalStateException("redis unavailable")).when(chatMemory).clear("conv-1");

        mockMvc.perform(delete("/api/chat/memory/conv-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("redis unavailable"));
    }
}
