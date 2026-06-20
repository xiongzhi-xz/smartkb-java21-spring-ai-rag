package com.smartkb.config;

import com.smartkb.agent.domain.AgentTaskException;
import com.smartkb.agent.domain.CodeContextException;
import com.smartkb.agent.domain.EvalCaseRunException;
import com.smartkb.agent.domain.MemoryRecordException;
import com.smartkb.agent.domain.ProjectIntakeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 统一处理所有 Controller 抛出的异常，返回友好的错误信息
 * <p>
 * 生产级要求：
 * - 不泄露敏感信息（堆栈信息只记录日志）
 * - 返回规范的错误格式
 * - 区分不同异常类型（400/404/500）
 * - AI 模型异常给出用户可理解的提示
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 Project Intake 参数和安全边界异常。
     */
    @ExceptionHandler(ProjectIntakeException.class)
    public ResponseEntity<Map<String, Object>> handleProjectIntakeException(ProjectIntakeException e) {
        log.warn("Project Intake 请求失败: code={}, message={}", e.code(), e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("code", e.code());

        return ResponseEntity.status(e.status()).body(response);
    }

    @ExceptionHandler(AgentTaskException.class)
    public ResponseEntity<Map<String, Object>> handleAgentTaskException(AgentTaskException e) {
        log.warn("Agent Task 请求失败: code={}, message={}", e.code(), e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("code", e.code());

        return ResponseEntity.status(e.status()).body(response);
    }

    @ExceptionHandler(MemoryRecordException.class)
    public ResponseEntity<Map<String, Object>> handleMemoryRecordException(MemoryRecordException e) {
        log.warn("Memory Record 请求失败: code={}, message={}", e.code(), e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("code", e.code());

        return ResponseEntity.status(e.status()).body(response);
    }

    @ExceptionHandler(EvalCaseRunException.class)
    public ResponseEntity<Map<String, Object>> handleEvalCaseRunException(EvalCaseRunException e) {
        log.warn("Eval Case Run request failed: code={}, message={}", e.code(), e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("code", e.code());

        return ResponseEntity.status(e.status()).body(response);
    }

    /**
     * 处理 AI 模型非瞬态异常（如 API Key 无效、模型不存在、配额耗尽等）
     * <p>
     * 典型场景：
     * - API Key 错误 → 401
     * - 模型名称拼写错误 → 404
     * - 账户余额不足 → 402/429
     * - 请求参数不合规 → 400
     * <p>
     * 返回 503（服务不可用）而非 500，因为这是上游依赖问题
     */
    @ExceptionHandler(CodeContextException.class)
    public ResponseEntity<Map<String, Object>> handleCodeContextException(CodeContextException e) {
        log.warn("Code Context request failed: code={}, message={}", e.code(), e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("code", e.code());

        return ResponseEntity.status(e.status()).body(response);
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<Map<String, Object>> handleNonTransientAiException(NonTransientAiException e) {
        log.error("AI 模型访问失败（非瞬态）：{}", e.getMessage(), e);

        String userMessage = classifyAiError(e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", userMessage);
        response.put("code", "AI_MODEL_ERROR");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * 处理 AI 模型瞬态异常（如网络超时、临时限流等）
     * <p>
     * 瞬态异常理论上可重试成功，但当前不做自动重试，
     * 返回提示让用户稍后再试
     */
    @ExceptionHandler(TransientAiException.class)
    public ResponseEntity<Map<String, Object>> handleTransientAiException(TransientAiException e) {
        log.error("AI 模型访问失败（瞬态）：{}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "AI 模型暂时不可用，请稍后再试。如果持续出现，请检查网络连接或模型服务状态。");
        response.put("code", "AI_MODEL_TEMPORARY_ERROR");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * 处理静态资源 404，避免 favicon 等浏览器自动请求被记录为系统异常。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException e) {
        log.debug("静态资源不存在: {}", e.getResourcePath());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "资源不存在");
        response.put("code", "RESOURCE_NOT_FOUND");

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.error("文件上传大小超限", e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "文件大小超过限制（最大 50MB）");
        response.put("code", "FILE_TOO_LARGE");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.error("非法参数", e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage());
        response.put("code", "INVALID_ARGUMENT");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理运行时异常（业务异常）
     * <p>
     * 如果异常消息中包含 AI 模型相关关键词，给出更友好的提示
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("业务异常", e);

        String message = e.getMessage() != null ? e.getMessage() : "系统内部错误";
        // 如果 RuntimeException 包裹了 AI 调用失败，给出友好提示
        if (isAiRelatedException(e)) {
            message = classifyAiError(message);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("code", isAiRelatedException(e) ? "AI_MODEL_ERROR" : "RUNTIME_ERROR");

        return ResponseEntity.status(isAiRelatedException(e)
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        log.error("系统异常", e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "系统内部错误，请联系管理员");
        response.put("code", "INTERNAL_ERROR");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ========== AI 异常分类 ==========

    /**
     * 根据 AI 异常消息自动识别错误类型，返回用户可理解的提示
     * <p>
     * 分类逻辑：匹配常见 HTTP 状态码和错误关键词
     *
     * @param rawMessage 原始异常消息
     * @return 用户可见的友好提示
     */
    private String classifyAiError(String rawMessage) {
        if (rawMessage == null) {
            return "AI 模型服务暂时不可用，请稍后再试。";
        }

        String msg = rawMessage.toLowerCase();

        // 401 / 403 - 认证/授权失败
        if (msg.contains("401") || msg.contains("unauthorized") || msg.contains("authentication")
                || msg.contains("invalid api key") || msg.contains("invalid_api_key")
                || msg.contains("incorrect api key")) {
            return "AI 模型认证失败，请检查 API Key 配置是否正确。";
        }

        // 402 / 429 - 余额不足 / 限流
        if (msg.contains("402") || msg.contains("insufficient_quota") || msg.contains("quota")
                || msg.contains("billing") || msg.contains("insufficient")
                || msg.contains("429") || msg.contains("rate_limit") || msg.contains("rate limit")
                || msg.contains("too many requests")) {
            return "AI 模型调用额度不足或被限流，请检查账户余额或稍后再试。";
        }

        // 404 - 模型不存在
        if (msg.contains("404") || msg.contains("model_not_found") || msg.contains("model not found")
                || msg.contains("does not exist")) {
            return "AI 模型不存在，请检查模型名称配置是否正确。";
        }

        // 400 - 请求参数错误
        if (msg.contains("400") || msg.contains("bad request") || msg.contains("invalid request")) {
            return "AI 模型请求参数错误，请检查模型配置。";
        }

        // 连接超时 / 网络错误
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("connection")
                || msg.contains("refused") || msg.contains("unreachable") || msg.contains("connect")
                || msg.contains("socket") || msg.contains("i/o error")) {
            return "无法连接 AI 模型服务，请检查网络连接和模型服务地址是否正确。";
        }

        // 500/502/503 - 上游服务错误
        if (msg.contains("500") || msg.contains("502") || msg.contains("503")
                || msg.contains("internal server error") || msg.contains("bad gateway")
                || msg.contains("service unavailable") || msg.contains("server error")) {
            return "AI 模型服务暂时不可用，请稍后再试。";
        }

        // 通用兜底
        return "AI 模型服务访问异常，请稍后再试。如持续出现，请检查模型配置或查看日志获取详情。";
    }

    /**
     * 判断 RuntimeException 是否与 AI 模型调用相关
     * <p>
     * 通过异常链检查：如果 cause 链中有 Spring AI 异常类，则认为是 AI 相关
     */
    private boolean isAiRelatedException(Throwable e) {
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 10) {
            String className = current.getClass().getName();
            if (className.startsWith("org.springframework.ai.")
                    || className.contains("AiException")
                    || className.contains("OpenAiApi")) {
                return true;
            }

            // 也检查消息中是否包含 AI 调用相关关键词
            String message = current.getMessage();
            if (message != null) {
                String msg = message.toLowerCase();
                if (msg.contains("ai model") || msg.contains("chat completion")
                        || msg.contains("openai") || msg.contains("embedding")
                        || msg.contains("chat model") || msg.contains("llm")) {
                    return true;
                }
            }

            current = current.getCause();
            depth++;
        }
        return false;
    }
}
