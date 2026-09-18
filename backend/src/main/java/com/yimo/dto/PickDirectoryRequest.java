package com.yimo.dto;

/**
 * 弹出目录选择窗口的请求。
 *
 * @param initialPath 窗口打开时定位到哪个目录，可为 null（则用系统默认位置）
 */
public record PickDirectoryRequest(String initialPath) {
}
