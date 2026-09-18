package com.yimo.storage;

import java.util.Map;

/**
 * 解析后的 Markdown：元数据 + 正文。
 *
 * @param frontmatter 文件头部的 YAML 字段。没有 frontmatter 时是空 Map（不是 null）
 * @param body        去掉 frontmatter 之后的正文
 */
public record ParsedMarkdown(Map<String, Object> frontmatter, String body) {
}
