package com.smartkb.agent.controller;

import com.smartkb.agent.application.CodeContextService;
import com.smartkb.agent.domain.CodeContextException;
import com.smartkb.agent.domain.CodeSearchRequest;
import com.smartkb.agent.domain.CodeSearchResponse;
import com.smartkb.agent.domain.CodeTreeRequest;
import com.smartkb.agent.domain.CodeTreeResponse;
import com.smartkb.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodeContextController.class)
@Import(GlobalExceptionHandler.class)
class CodeContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CodeContextService codeContextService;

    @Test
    void shouldReturnCodeTree() throws Exception {
        when(codeContextService.tree(any(CodeTreeRequest.class))).thenReturn(new CodeTreeResponse(
                true,
                "E:/project/work/job/demo",
                List.of(new CodeTreeResponse.CodeFile("src/main/java/App.java", 42, "java")),
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/agent/code/tree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rootPath": "E:/project/work/job/demo",
                                  "maxFiles": 100,
                                  "maxDepth": 6
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.files[0].path").value("src/main/java/App.java"))
                .andExpect(jsonPath("$.files[0].extension").value("java"));

        ArgumentCaptor<CodeTreeRequest> captor = ArgumentCaptor.forClass(CodeTreeRequest.class);
        verify(codeContextService).tree(captor.capture());
        assertEquals("E:/project/work/job/demo", captor.getValue().rootPath());
        assertEquals(100, captor.getValue().maxFiles());
        assertEquals(6, captor.getValue().maxDepth());
    }

    @Test
    void shouldReturnCodeSearchMatches() throws Exception {
        when(codeContextService.search(any(CodeSearchRequest.class))).thenReturn(new CodeSearchResponse(
                true,
                "E:/project/work/job/demo",
                "TicketService",
                List.of(new CodeSearchResponse.CodeMatch("src/main/java/TicketService.java", 12, "class TicketService {}")),
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/agent/code/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rootPath": "E:/project/work/job/demo",
                                  "query": "TicketService",
                                  "maxResults": 20,
                                  "maxFileBytes": 65536
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.query").value("TicketService"))
                .andExpect(jsonPath("$.matches[0].path").value("src/main/java/TicketService.java"))
                .andExpect(jsonPath("$.matches[0].lineNumber").value(12));

        ArgumentCaptor<CodeSearchRequest> captor = ArgumentCaptor.forClass(CodeSearchRequest.class);
        verify(codeContextService).search(captor.capture());
        assertEquals("TicketService", captor.getValue().query());
        assertEquals(20, captor.getValue().maxResults());
    }

    @Test
    void shouldReturnCodeContextErrorResponse() throws Exception {
        when(codeContextService.search(any(CodeSearchRequest.class))).thenThrow(
                new CodeContextException("CODE_SEARCH_QUERY_REQUIRED", HttpStatus.BAD_REQUEST, "query is required")
        );

        mockMvc.perform(post("/api/agent/code/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CODE_SEARCH_QUERY_REQUIRED"))
                .andExpect(jsonPath("$.error").value("query is required"));
    }
}
