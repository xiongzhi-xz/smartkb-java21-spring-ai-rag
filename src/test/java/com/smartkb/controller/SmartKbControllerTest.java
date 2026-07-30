package com.smartkb.controller;

import com.smartkb.application.DocumentIngestionSubmissionService;
import com.smartkb.application.EnterpriseChatService;
import com.smartkb.application.RetrievalTraceQueryService;
import com.smartkb.application.DocumentUploadResult;
import com.smartkb.application.DocumentDeletionConflictException;
import com.smartkb.application.DocumentDeletionResult;
import com.smartkb.application.DocumentDeletionService;
import com.smartkb.application.DocumentRetryResult;
import com.smartkb.application.DocumentRetryService;
import com.smartkb.config.GlobalExceptionHandler;
import com.smartkb.domain.AdvancedRagMetrics;
import com.smartkb.domain.AdvancedRagResult;
import com.smartkb.domain.AnswerEvaluationReport;
import com.smartkb.domain.RagEvalCase;
import com.smartkb.domain.RagEvalCaseResult;
import com.smartkb.domain.RagEvalReport;
import com.smartkb.domain.IngestionJobStatus;
import com.smartkb.service.AdvancedRagService;
import com.smartkb.service.AnswerEvaluationService;
import com.smartkb.service.DocumentManagementService;
import com.smartkb.service.RagEvaluationService;
import com.smartkb.service.RagService;
import com.smartkb.service.SmartKbMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SmartKbController.class)
@ActiveProfiles("local-demo")
@Import(GlobalExceptionHandler.class)
class SmartKbControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RagService ragService;

    @MockBean
    private EnterpriseChatService enterpriseChatService;

    @MockBean
    private RetrievalTraceQueryService retrievalTraceQueryService;

    @MockBean
    private AdvancedRagService advancedRagService;

    @MockBean
    private RagEvaluationService ragEvaluationService;

    @MockBean
    private AnswerEvaluationService answerEvaluationService;

    @MockBean
    private DocumentIngestionSubmissionService documentIngestionSubmissionService;

    @MockBean
    private DocumentDeletionService documentDeletionService;

    @MockBean
    private DocumentRetryService documentRetryService;

    @MockBean
    private DocumentManagementService documentManagementService;

    @MockBean
    private ChatMemory chatMemory;

    @MockBean
    private SmartKbMetricsService metricsService;

    @Test
    void shouldAcceptDocumentForAsynchronousIngestion() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.md",
                "text/markdown",
                "SmartKB content".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(documentIngestionSubmissionService.submit(
                any(), eq("demo.md"), eq("md"), eq("text/markdown"), eq(file.getSize())))
                .thenReturn(new DocumentUploadResult(
                        documentId,
                        jobId,
                        "demo.md",
                        IngestionJobStatus.PENDING,
                        true));

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.queued").value(true));

        verify(documentIngestionSubmissionService).submit(
                any(), eq("demo.md"), eq("md"), eq("text/markdown"), eq(file.getSize()));
    }

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
                0.91,
                false,
                "",
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
                .andExpect(jsonPath("$.retrievedCount").value(1))
                .andExpect(jsonPath("$.confidence").value(0.91))
                .andExpect(jsonPath("$.refused").value(false));

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

    @Test
    void shouldReturnEnterpriseRetrievalTrace() throws Exception {
        UUID traceId = UUID.randomUUID();
        when(retrievalTraceQueryService.get(traceId)).thenReturn(Map.of(
                "traceId", traceId.toString(), "query", "rewritten", "candidates", List.of(Map.of("chunkId", "chunk-1")),
                "retrievalMode", "hybrid", "latencyMs", 12, "createdAt", "2026-07-22T00:00:00Z"));

        mockMvc.perform(get("/api/retrieval-traces/{traceId}", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(traceId.toString()))
                .andExpect(jsonPath("$.candidates[0].chunkId").value("chunk-1"));
    }

    @Test
    void shouldRouteUuidDocumentDetailToEnterpriseQuery() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentManagementService.getDocumentDetail(documentId))
                .thenReturn(Map.of(
                        "documentId", documentId.toString(),
                        "fileName", "demo.md",
                        "status", "READY",
                        "chunkCount", 2));

        mockMvc.perform(get("/api/documents/{documentId}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.document.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.document.status").value("READY"))
                .andExpect(jsonPath("$.document.chunkCount").value(2));

        verify(documentManagementService).getDocumentDetail(documentId);
    }

    @Test
    void shouldKeepFileNameDocumentDetailCompatibility() throws Exception {
        when(documentManagementService.getDocumentDetail("legacy.md"))
                .thenReturn(Map.of("fileName", "legacy.md", "chunkCount", 1, "chunks", List.of()));

        mockMvc.perform(get("/api/documents/legacy.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.document.fileName").value("legacy.md"));

        verify(documentManagementService).getDocumentDetail("legacy.md");
    }

    @Test
    void shouldReturnNotFoundForMissingEnterpriseDocument() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentManagementService.getDocumentDetail(documentId))
                .thenThrow(new IllegalArgumentException("文档不存在: " + documentId));

        mockMvc.perform(get("/api/documents/{documentId}", documentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("文档不存在: " + documentId));
    }

    @Test
    void shouldAcceptFailedDocumentRetry() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(documentRetryService.retry(documentId)).thenReturn(
                new DocumentRetryResult(documentId, jobId, IngestionJobStatus.RETRYING, 1, true));

        mockMvc.perform(post("/api/documents/{documentId}/retry", documentId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("RETRYING"))
                .andExpect(jsonPath("$.retryCount").value(1))
                .andExpect(jsonPath("$.queued").value(true));
    }

    @Test
    void shouldReturnConflictWhenDocumentCannotBeRetried() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentRetryService.retry(documentId))
                .thenThrow(new IllegalStateException("文档当前不可重试: " + documentId + ", status=READY"));

        mockMvc.perform(post("/api/documents/{documentId}/retry", documentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("文档当前不可重试: " + documentId + ", status=READY"));
    }

    @Test
    void shouldDeleteEnterpriseDocumentByUuid() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentDeletionService.delete(documentId))
                .thenReturn(new DocumentDeletionResult(documentId, "demo.md", 3));

        mockMvc.perform(delete("/api/documents/{documentId}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.fileName").value("demo.md"))
                .andExpect(jsonPath("$.deletedChunks").value(3));

        verify(documentDeletionService).delete(documentId);
    }

    @Test
    void shouldReturnConflictWhenEnterpriseDocumentIsActive() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentDeletionService.delete(documentId))
                .thenThrow(new DocumentDeletionConflictException("文档当前不可删除"));

        mockMvc.perform(delete("/api/documents/{documentId}", documentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("文档当前不可删除"));
    }

    @Test
    void shouldKeepLegacyFileNameDeletionCompatibility() throws Exception {
        when(documentManagementService.deleteDocument("legacy.md")).thenReturn(2);

        mockMvc.perform(delete("/api/documents/legacy.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("legacy.md"))
                .andExpect(jsonPath("$.deletedChunks").value(2));

        verify(documentManagementService).deleteDocument("legacy.md");
    }

    @Test
    void shouldListRagEvalCases() throws Exception {
        when(ragEvaluationService.defaultCases()).thenReturn(List.of(new RagEvalCase(
                "RAG-E03",
                "查询改写在 Advanced RAG 中解决什么问题？",
                "advanced-rag-demo.md",
                List.of("chunk-07"),
                List.of("查询改写")
        )));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/rag/eval/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("RAG-E03"))
                .andExpect(jsonPath("$[0].expectedChunkIds[0]").value("chunk-07"))
                .andExpect(jsonPath("$[0].expectedKeywords[0]").value("查询改写"));
    }

    @Test
    void shouldRunRagEvalReport() throws Exception {
        when(ragEvaluationService.runEvaluation(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ragEvalReport());

        mockMvc.perform(post("/api/rag/eval/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").value(1))
                .andExpect(jsonPath("$.advancedHitRate").value(1.0))
                .andExpect(jsonPath("$.advancedMrr").value(1.0))
                .andExpect(jsonPath("$.advancedTop1HitCount").value(1))
                .andExpect(jsonPath("$.cases[0].advancedHit").value(true));
    }

    @Test
    void shouldGetDefaultRagEvalReport() throws Exception {
        when(ragEvaluationService.runDefaultEvaluation()).thenReturn(ragEvalReport());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/rag/eval/report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").value(1))
                .andExpect(jsonPath("$.citationHitCount").value(1))
                .andExpect(jsonPath("$.cases[0].failureReason").value("通过"));
    }

    @Test
    void shouldEvaluateGeneratedAnswer() throws Exception {
        when(answerEvaluationService.evaluate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AnswerEvaluationReport(
                        0.9, 0.8, 0.7, 0.8,
                        "supported", "direct", "relevant"));

        mockMvc.perform(post("/api/rag/eval/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is RAG?",
                                  "answer": "RAG grounds answers in retrieved evidence.",
                                  "contexts": ["RAG retrieves evidence before generation."]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faithfulness").value(0.9))
                .andExpect(jsonPath("$.answerRelevance").value(0.8))
                .andExpect(jsonPath("$.contextRelevance").value(0.7))
                .andExpect(jsonPath("$.overallScore").value(0.8));
    }

    private RagEvalReport ragEvalReport() {
        return new RagEvalReport(
                1,
                0,
                1,
                1,
                1,
                0,
                1,
                0.0,
                1.0,
                1.0,
                0.0,
                1.0,
                0.0,
                1.0,
                List.of(new RagEvalCaseResult(
                        "RAG-E03",
                        "查询改写在 Advanced RAG 中解决什么问题？",
                        "advanced-rag-demo.md",
                        List.of("chunk-07"),
                        List.of("查询改写"),
                        false,
                        true,
                        true,
                        1,
                        1,
                        List.of(),
                        List.of("chunk-07"),
                        0,
                        1,
                        false,
                        true,
                        0.0,
                        1.0,
                        "bge-rule-hybrid",
                        "Advanced RAG 查询改写用于提升检索质量",
                        List.of(),
                        List.of("查询改写"),
                        "通过",
                        List.of()
                ))
        );
    }
}
