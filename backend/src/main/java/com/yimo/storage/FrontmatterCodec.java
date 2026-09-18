package com.yimo.storage;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读写 Markdown 的 frontmatter。
 *
 * <p>这一版只实现「读」，写回留到迭代 4（保存正文）时再做。
 */
@Component
public class FrontmatterCodec {

    /**
     * 匹配文件开头的 frontmatter 块。
     *
     * <p>^---\s*\r?\n      开头的 --- 加换行（\r? 兼容 Windows 的 CRLF）
     * <p>(.*?)             中间内容，非贪婪，所以只吃到第一个结尾的 ---
     * <p>\r?\n---\s*\r?\n? 结尾的 --- 加换行
     *
     * <p>DOTALL 让点号能匹配换行符，否则中间的多行内容匹配不到。
     */
    private static final Pattern FM = Pattern.compile(
            "^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n?", Pattern.DOTALL);

    private final Yaml yaml = new Yaml();

    public ParsedMarkdown parse(String raw) {
        String text = stripBom(raw);

        Matcher m = FM.matcher(text);
        if (!m.find()) {
            // 没有 frontmatter：返回空元数据 + 全部内容当正文
            return new ParsedMarkdown(new LinkedHashMap<>(), text);
        }

        Map<String, Object> fm = yaml.load(m.group(1));

        // 只有一行 --- 时 yaml.load 返回 null，兜底成空 Map。
        // 再包一层 LinkedHashMap 是保险：SnakeYAML 返回的类型不保证有序
        return new ParsedMarkdown(
                fm != null ? new LinkedHashMap<>(fm) : new LinkedHashMap<>(),
                text.substring(m.end()));
    }

    /**
     * 剥掉 UTF-8 BOM。
     *
     * <p>不剥的话，开头的 --- 前面会多一个不可见字符，正则匹配失败，
     * 整份文件被误判成没有元数据。
     */
    private String stripBom(String s) {
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }
}
