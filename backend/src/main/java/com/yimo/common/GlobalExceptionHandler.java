package com.yimo.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理。所有异常统一翻译成 06-api.md 定义的错误体格式：
 * { error, message, details, timestamp }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException e) {
        log.warn("业务异常 [{}] {}", e.code().name(), e.getMessage());
        return ResponseEntity.status(e.code().status())
                .body(body(e.code().name(), e.getMessage(), e.details()));
    }

    /** @Valid 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 {}", message);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(body(ErrorCode.VALIDATION_FAILED.name(), message, Map.of()));
    }

    /** 兜底。任何没被上面捕获的异常都到这里 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(body(ErrorCode.INTERNAL_ERROR.name(),
                           ErrorCode.INTERNAL_ERROR.message(),
                           Map.of()));
    }

    private Map<String, Object> body(String error, String message, Map<String, Object> details) {
        // LinkedHashMap 保证字段顺序稳定，方便前端调试时肉眼阅读
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("message", message);
        m.put("details", details);
        m.put("timestamp", OffsetDateTime.now().toString());
        return m;
    }
}
