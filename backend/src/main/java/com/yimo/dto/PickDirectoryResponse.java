package com.yimo.dto;

/**
 * 目录选择窗口的返回。
 *
 * @param picked 用户是否选了目录（点取消则为 false）
 * @param path   选中的绝对路径，未选中时为 null
 */
public record PickDirectoryResponse(boolean picked, String path) {
}
