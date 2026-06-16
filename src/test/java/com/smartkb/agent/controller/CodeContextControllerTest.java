package com.smartkb.agent.controller;

import com.smartkb.agent.application.CodeContextService;
import com.smartkb.agent.domain.CodeChunkRequest;
import com.smartkb.agent.domain.CodeChunkResponse;
import com.smartkb.agent.domain.CodeContextException;
import com.smartkb.agent.domain.CodeDiffRequest;
import com.smartkb.agent.domain.CodeDiffResponse;
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
    void shouldReturnCodeDiffEvidence() throws Exception {
        when(codeContextService.diff(any(CodeDiffRequest.class))).thenReturn(new CodeDiffResponse(
                true,
                "E:/project/work/job/demo",
                true,
                "reserveTicket",
                List.of(new CodeDiffResponse.DiffFile(
                        "src/main/java/TicketService.java",
                        List.of(new CodeDiffResponse.DiffLine("add", null, 12, "void reserveTicket() {}"))
                )),
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/agent/code/diff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rootPath": "E:/project/work/job/demo",
                                  "query": "reserveTicket",
                                  "maxLines": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.gitRepository").value(true))
                .andExpect(jsonPath("$.files[0].path").value("src/main/java/TicketService.java"))
                .andExpect(jsonPath("$.files[0].lines[0].type").value("add"))
                .andExpect(jsonPath("$.files[0].lines[0].newLineNumber").value(12));

        ArgumentCaptor<CodeDiffRequest> captor = ArgumentCaptor.forClass(CodeDiffRequest.class);
        verify(codeContextService).diff(captor.capture());
        assertEquals("reserveTicket", captor.getValue().query());
        assertEquals(20, captor.getValue().maxLines());
    }

    @Test
    void shouldReturnCodeChunks() throws Exception {
        when(codeContextService.chunks(any(CodeChunkRequest.class))).thenReturn(new CodeChunkResponse(
                true,
                "E:/project/work/job/demo",
                List.of(new CodeChunkResponse.CodeChunk("src/main/java/App.java", 1, 3, "class App {}")),
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/agent/code/chunks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rootPath": "E:/project/work/job/demo",
                                  "maxChunks": 20,
                                  "maxFileBytes": 65536,
                                  "maxChunkChars": 2000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.chunks[0].path").value("src/main/java/App.java"))
                .andExpect(jsonPath("$.chunks[0].startLine").value(1))
                .andExpect(jsonPath("$.chunks[0].endLine").value(3));

        ArgumentCaptor<CodeChunkRequest> captor = ArgumentCaptor.forClass(CodeChunkRequest.class);
        verify(codeContextService).chunks(captor.capture());
        assertEquals(20, captor.getValue().maxChunks());
        assertEquals(2000, captor.getValue().maxChunkChars());
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
