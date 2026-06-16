package com.smartkb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartkb.domain.AdvancedRagMetrics;
import com.smartkb.domain.AdvancedRagResult;
import com.smartkb.domain.ReferenceChunk;
import com.smartkb.service.AdvancedRagService;
import com.smartkb.service.DocumentManagementService;
import com.smartkb.service.RagService;
import com.smartkb.service.SmartKbMetricsService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * SmartKB REST API 控制器
 * <p>
 * 提供以下接口：
 * 1. POST /api/documents/upload - 上传文档到知识库
 * 2. POST /api/chat - 单轮 RAG 问答
 * 3. POST /api/chat/conversation - 多轮对话（带上下文记忆）
 * <p>
 * Virtual Threads 应用：
 * - Spring Boot 3.3 已启用虚拟线程（application.yml 配置）
 * - 所有 Controller 方法自动运行在虚拟线程上
 * - RagService 的文档处理任务也使用虚拟线程池，不阻塞 Controller 线程
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SmartKbController {

    private final RagService ragService;
    private final AdvancedRagService advancedRagService;
    private final DocumentManagementService documentManagementService;
    private final ChatMemory chatMemory;
    private final SmartKbMetricsService metricsService;
    private final ObjectMapper objectMapper;

    /**
     * 上传文档到知识库
     * <p>
     * 处理流程：
     * 1. 接收 MultipartFile
     * 2. 调用 RagService.addDocument（解析 → Embedding → 存储）
     * 3. 返回处理结果（文档块数量）
     * <p>
     * Virtual Threads 优化：
     * - Controller 方法运行在虚拟线程（不阻塞平台线程）
     * - 文档处理任务使用虚拟线程池并发执行
     * - 支持高并发上传（传统线程池无法做到）
     *
     * @param file 上传的文档文件
     * @return 处理结果
     */
    @PostMapping("/documents/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        log.info("接收文档上传请求: 文件名={}, 大小={} bytes", file.getOriginalFilename(), file.getSize());

        try {
            // 1. 检查文件类型
            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件名不能为空"));
            }

            String fileType = getFileExtension(fileName);
            if (!isSupportedFileType(fileType)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "不支持的文件类型: " + fileType,
                        "supportedTypes", "pdf, docx, md, txt"
                ));
            }

            // 2. 转换为 Spring Resource
            Resource resource = file.getResource();

            // 3. 调用 RagService 处理文档
            int chunkCount = ragService.addDocument(resource, fileType, null);

            // 4. 返回成功响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileName", fileName);
            response.put("fileType", fileType);
            response.put("chunkCount", chunkCount);
            response.put("message", "文档上传成功");

            log.info("文档上传成功: {}, {} chunks", fileName, chunkCount);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("文档上传失败: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 单轮 RAG 问答
     * <p>
     * 流程说明：
     * 1. 接收用户问题
     * 2. ChatClient 通过 QuestionAnswerAdvisor 自动执行 RAG 流程：
     *    - 向量检索相关文档
     *    - 注入检索结果到 Prompt
     *    - LLM 基于文档生成答案
     * 3. 返回答案
     *
     * @param request 问答请求（包含 question 字段）
     * @return 答案
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("接收问答请求: {}", request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        try {
            // 调用 RagService 进行 RAG 问答
            String answer = ragService.query(request.getQuestion());

            ChatResponse response = new ChatResponse();
            response.setAnswer(answer);
            response.setContent(answer);  // 兼容前端
            response.setSuccess(true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("问答失败: {}", request.getQuestion(), e);
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError(e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 多轮对话（带上下文记忆）
     * <p>
     * 与单轮问答的区别：
     * - 使用 conversationId 管理会话
     * - ChatMemoryAdvisor 自动加载历史消息
     * - 支持上下文关联的连续提问
     * <p>
     * 使用示例：
     * 1. 用户问："Java 21 有哪些新特性？"
     * 2. 用户问："Virtual Threads 怎么用？"（基于上文理解是 Java 21 的特性）
     *
     * @param request 对话请求（包含 question 和 conversationId）
     * @return 答案
     */
    @PostMapping("/chat/conversation")
    public ResponseEntity<ChatResponse> chatWithContext(@RequestBody ConversationRequest request) {
        log.info("接收多轮对话请求: conversationId={}, question={}",
                request.getConversationId(),
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));
        metricsService.recordConversationRequest();

        try {
            // 如果没有提供 conversationId，生成一个新的
            String conversationId = resolveConversationId(request.getConversationId());

            // 调用 RagService 进行多轮 RAG 问答
            String answer = ragService.queryWithContext(request.getQuestion(), conversationId);

            ChatResponse response = new ChatResponse();
            response.setAnswer(answer);
            response.setContent(answer);
            response.setConversationId(conversationId);
            response.setSuccess(true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("多轮对话失败: conversationId={}, question={}",
                    request.getConversationId(), request.getQuestion(), e);
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError(e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * 多轮对话流式输出。
     * <p>
     * SSE 事件：
     * - conversation：返回 conversationId
     * - token：返回增量文本
     * - done：回答结束
     * - error：回答失败
     */
    @PostMapping(value = "/chat/conversation/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatWithContextStream(@RequestBody ConversationRequest request) {
        log.info("接收多轮流式对话请求: conversationId={}, question={}",
                request.getConversationId(),
                request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        String conversationId = resolveConversationId(request.getConversationId());

        String finalConversationId = conversationId;
        StreamingResponseBody body = outputStream -> {
            CountDownLatch done = new CountDownLatch(1);

            try {
                writeSseEvent(outputStream, "conversation", Map.of("conversationId", finalConversationId));

                ragService.queryWithContextStream(request.getQuestion(), finalConversationId)
                        .subscribe(
                                chunk -> writeSseEventUnchecked(outputStream, "token", Map.of("content", chunk)),
                                error -> {
                                    try {
                                        log.error("流式对话失败: conversationId={}", finalConversationId, error);
                                        writeSseEventUnchecked(outputStream, "error", Map.of("error", error.getMessage()));
                                    } finally {
                                        done.countDown();
                                    }
                                },
                                () -> {
                                    try {
                                        writeSseEventUnchecked(outputStream, "done", Map.of("success", true));
                                    } finally {
                                        done.countDown();
                                    }
                                }
                        );

                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writeSseEvent(outputStream, "error", Map.of("error", "流式响应被中断"));
            } catch (Exception e) {
                log.error("创建流式对话失败: conversationId={}", finalConversationId, e);
                writeSseEvent(outputStream, "error", Map.of("error", e.getMessage()));
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    private void writeSseEventUnchecked(OutputStream outputStream, String event, Object data) {
        try {
            writeSseEvent(outputStream, event, data);
        } catch (IOException e) {
            throw new RuntimeException("写入流式响应失败", e);
        }
    }

    private void writeSseEvent(OutputStream outputStream, String event, Object data) throws IOException {
        String payload = objectMapper.writeValueAsString(data);
        String message = "event: " + event + "\n" + "data: " + payload + "\n\n";
        outputStream.write(message.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    // ========== 会话记忆管理接口 ==========

    /**
     * 清除指定会话的 ChatMemory
     * <p>
     * 当用户点击"新会话"时，前端调用此接口清除 Redis 中对应的会话数据。
     * 确保新建会话后不会再读取到旧对话上下文。
     *
     * @param conversationId 会话 ID
     * @return 清除结果
     */
    @DeleteMapping("/chat/memory/{conversationId}")
    public ResponseEntity<Map<String, Object>> clearChatMemory(@PathVariable String conversationId) {
        log.info("清除会话记忆: conversationId={}", conversationId);

        try {
            chatMemory.clear(conversationId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("conversationId", conversationId);
            response.put("message", "会话记忆已清除");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("清除会话记忆失败: conversationId={}", conversationId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ========== 文档管理接口 ==========

    /**
     * 查询所有已上传文档
     *
     * @return 文档列表
     */
    @GetMapping("/documents")
    public ResponseEntity<Map<String, Object>> listDocuments() {
        log.info("查询文档列表");

        try {
            List<Map<String, Object>> documents = documentManagementService.listDocuments();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("documents", documents);
            response.put("count", documents.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("查询文档列表失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 查询文档详情
     *
     * @param fileName 文件名
     * @return 文档详情
     */
    @GetMapping("/documents/{fileName}")
    public ResponseEntity<Map<String, Object>> getDocumentDetail(@PathVariable String fileName) {
        log.info("查询文档详情: {}", fileName);

        try {
            Map<String, Object> detail = documentManagementService.getDocumentDetail(fileName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("document", detail);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("查询文档详情失败: {}", fileName, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 删除文档
     *
     * @param fileName 文件名
     * @return 删除结果
     */
    @DeleteMapping("/documents/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String fileName) {
        log.info("删除文档: {}", fileName);

        try {
            int deletedCount = documentManagementService.deleteDocument(fileName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileName", fileName);
            response.put("deletedChunks", deletedCount);
            response.put("message", "文档删除成功");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("删除文档失败: {}", fileName, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 查询文档统计信息
     *
     * @return 统计信息
     */
    @GetMapping("/documents/stats")
    public ResponseEntity<Map<String, Object>> getDocumentStatistics() {
        log.info("查询文档统计信息");

        try {
            Map<String, Object> stats = documentManagementService.getStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", stats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("查询统计信息失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 检查是否支持该文件类型
     */
    private boolean isSupportedFileType(String fileType) {
        return fileType.matches("pdf|docx?|md|txt");
    }

    /**
     * 解析 conversationId：如果为空则生成新的 UUID
     * <p>
     * 统一 conversation / advanced 两种模式的 conversationId 生成逻辑
     *
     * @param conversationId 前端传入的 conversationId（可能为 null）
     * @return 有效的 conversationId
     */
    private String resolveConversationId(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return UUID.randomUUID().toString();
        }
        return conversationId;
    }

    /**
     * Advanced RAG 问答（支持查询改写 + 元数据过滤）
     * <p>
     * 与普通 /api/chat 的区别：
     * - 自动进行查询改写（提升检索准确率）
     * - 支持元数据过滤（如只搜索特定文档）
     * - 结果重排序
     * <p>
     * 适用场景：
     * - 需要高精度检索
     * - 需要指定文档范围
     * - 用户问题比较模糊
     *
     * @param request Advanced RAG 请求
     * @return 答案
     */
    @PostMapping("/chat/advanced")
    public ResponseEntity<ChatResponse> chatAdvanced(@RequestBody AdvancedChatRequest request) {
        log.info("接收 Advanced RAG 请求: {}", request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        try {
            // 生成/确认 conversationId
            String conversationId = resolveConversationId(request.getConversationId());

            // 调用 AdvancedRagService（使用 ChatMemory 管理历史）
            AdvancedRagResult result = advancedRagService.queryAdvancedWithDetails(
                    request.getQuestion(),
                    request.getMetadataFilter(),
                    conversationId
            );

            ChatResponse response = new ChatResponse();
            response.setAnswer(result.answer());
            response.setContent(result.answer());
            response.setConversationId(conversationId);
            response.setSources(result.sources());
            response.setReferences(result.references());
            response.setRewrittenQuery(result.rewrittenQuery());
            response.setRetrievedCount(result.retrievedCount());
            response.setMetrics(result.metrics());
            response.setSuccess(true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Advanced RAG 问答失败: {}", request.getQuestion(), e);
            ChatResponse errorResponse = new ChatResponse();
            errorResponse.setSuccess(false);
            errorResponse.setError(e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Advanced RAG 分阶段流式反馈。
     * <p>
     * SSE 事件：
     * - conversation：返回 conversationId
     * - stage：返回查询改写、检索、过滤、重排序、生成等阶段状态
     * - done：返回完整 Advanced RAG 结果（含 conversationId）
     * - error：返回失败原因
     */
    @PostMapping(value = "/chat/advanced/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatAdvancedStream(@RequestBody AdvancedChatRequest request) {
        log.info("接收 Advanced RAG 流式请求: {}", request.getQuestion().substring(0, Math.min(50, request.getQuestion().length())));

        String conversationId = resolveConversationId(request.getConversationId());
        String finalConversationId = conversationId;

        StreamingResponseBody body = outputStream -> {
            try {
                // 发送 conversationId 给前端（与 conversation 模式一致）
                writeSseEvent(outputStream, "conversation", Map.of("conversationId", finalConversationId));

                AdvancedRagResult result = advancedRagService.queryAdvancedWithDetails(
                        request.getQuestion(),
                        request.getMetadataFilter(),
                        finalConversationId,
                        stage -> writeSseEventUnchecked(outputStream, "stage", stage)
                );

                Map<String, Object> donePayload = buildAdvancedResponsePayload(result);
                donePayload.put("conversationId", finalConversationId);
                writeSseEvent(outputStream, "done", donePayload);
            } catch (Exception e) {
                log.error("Advanced RAG 流式问答失败: {}", request.getQuestion(), e);
                writeSseEvent(outputStream, "error", Map.of("error", e.getMessage()));
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    private Map<String, Object> buildAdvancedResponsePayload(AdvancedRagResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("answer", result.answer());
        payload.put("content", result.answer());
        payload.put("sources", result.sources());
        payload.put("references", result.references());
        payload.put("rewrittenQuery", result.rewrittenQuery());
        payload.put("retrievedCount", result.retrievedCount());
        payload.put("metrics", result.metrics());
        payload.put("success", true);
        return payload;
    }

    /**
     * 测试专用 RAG 接口（带详细调试信息）
     * <p>
     * 用于验证整个 RAG 链路是否正常工作：
     * - 向量检索是否生效
     * - Advisor 链路是否正确执行
     * - Virtual Threads 是否生效
     * <p>
     * 返回完整调试信息，便于排查问题
     *
     * @param request 测试请求
     * @return 详细调试信息
     */
    @PostMapping("/test/rag")
    public ResponseEntity<Map<String, Object>> testRag(@RequestBody ChatRequest request) {
        log.info("=== RAG 测试接口调用 ===");
        log.info("问题: {}", request.getQuestion());

        try {
            long startTime = System.currentTimeMillis();

            // 调用 RagService 进行测试
            Map<String, Object> testResult = ragService.queryWithDebugInfo(request.getQuestion());

            long duration = System.currentTimeMillis() - startTime;
            testResult.put("totalDuration", duration + " ms");
            testResult.put("success", true);

            log.info("=== RAG 测试完成，耗时: {} ms ===", duration);
            return ResponseEntity.ok(testResult);

        } catch (Exception e) {
            log.error("RAG 测试失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    // ========== DTO 定义 ==========

    /**
     * 单轮问答请求
     */
    @Data
    public static class ChatRequest {
        private String question;
    }

    /**
     * 多轮对话请求
     */
    @Data
    public static class ConversationRequest {
        private String question;
        private String conversationId;
    }

    /**
     * Advanced RAG 请求
     */
    @Data
    public static class AdvancedChatRequest {
        private String question;
        private Map<String, Object> metadataFilter;  // 元数据过滤条件（可选）
        private String history;  // 对话历史（可选，兼容旧调用）
        private String conversationId;  // 会话 ID（新增，用于 ChatMemory 读写）
    }

    /**
     * 问答响应
     */
    @Data
    public static class ChatResponse {
        private String answer;
        private String content;  // 兼容前端
        private String conversationId;
        private List<String> sources;
        private List<ReferenceChunk> references;
        private String rewrittenQuery;
        private Integer retrievedCount;
        private AdvancedRagMetrics metrics;
        private boolean success;
        private String error;
    }
}
