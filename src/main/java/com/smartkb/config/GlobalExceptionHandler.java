package com.smartkb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 *
 * @author SmartKB Team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.error("文件上传大小超限", e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "文件大小超过限制（最大 10MB）");
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
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("业务异常", e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", e.getMessage() != null ? e.getMessage() : "系统内部错误");
        response.put("code", "RUNTIME_ERROR");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
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
}
