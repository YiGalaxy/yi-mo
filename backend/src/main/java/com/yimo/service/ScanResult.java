package com.yimo.service;

import java.util.List;

/**
 * 扫描结果。
 *
 * @param total   扫到的文件总数
 * @param indexed 成功索引的章节数
 * @param errors  解析失败的文件相对路径，会展示给作者
 */
public record ScanResult(int total, int indexed, List<String> errors) {

    /** 是否有文件解析失败 */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
