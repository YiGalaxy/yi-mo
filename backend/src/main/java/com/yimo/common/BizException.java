package com.yimo.common;

import java.util.Map;

/**
 * 业务异常。由 GlobalExceptionHandler 统一翻译成 HTTP 响应。
 */
public class BizException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public BizException(ErrorCode code) {
        this(code, code.message(), Map.of());
    }

    public BizException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public BizException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
