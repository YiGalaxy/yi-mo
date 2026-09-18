package com.yimo.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。每个错误码绑定一个 HTTP 状态码和一句面向用户的中文说明。
 */
public enum ErrorCode {

    // ===== 书库 =====
    LIBRARY_NOT_FOUND(HttpStatus.NOT_FOUND, "书库不存在"),
    LIBRARY_PATH_INVALID(HttpStatus.BAD_REQUEST, "书库路径无效或不可读"),
    LIBRARY_PATH_DUPLICATE(HttpStatus.CONFLICT, "该路径已添加过"),

    // ===== 章节 =====
    CHAPTER_NOT_FOUND(HttpStatus.NOT_FOUND, "章节不存在"),
    CHAPTER_TITLE_DUPLICATE(HttpStatus.CONFLICT, "同目录下已有同名章节"),

    // ===== 文件 =====
    PATH_OUT_OF_BOUNDS(HttpStatus.FORBIDDEN, "路径越界"),
    FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件读取失败"),
    FILE_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件写入失败"),
    CONTENT_HASH_MISMATCH(HttpStatus.CONFLICT, "文件已被外部修改"),

    // ===== AI =====
    MODEL_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "尚未配置模型"),
    AI_CALL_FAILED(HttpStatus.BAD_GATEWAY, "模型调用失败"),
    AI_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "已达每日用量上限"),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "任务不存在"),
    TASK_ALREADY_RUNNING(HttpStatus.CONFLICT, "同类型任务正在执行"),

    // ===== 通用 =====
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误"),

    // ===== 原生对话框 =====
    PICKER_UNSUPPORTED(HttpStatus.BAD_REQUEST, "当前环境没有图形界面，请手动填写路径"),
    PICKER_BUSY(HttpStatus.CONFLICT, "已经有一个选择窗口打开了"),
    PICKER_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "打开选择窗口失败"),
    ;

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
