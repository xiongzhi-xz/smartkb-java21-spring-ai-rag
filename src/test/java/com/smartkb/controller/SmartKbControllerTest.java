package com.smartkb.controller;

import com.smartkb.config.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
