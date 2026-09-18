package com.yimo.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontmatterCodecTest {

    private final FrontmatterCodec codec = new FrontmatterCodec();

    @Test
    void parsesNormalFrontmatter() {
        String raw = """
                ---
                title: 第一章 雪夜
                order: 1
                ---

                　　正文内容
                """;

        ParsedMarkdown pm = codec.parse(raw);

        assertEquals("第一章 雪夜", pm.frontmatter().get("title"));
        assertEquals(1, pm.frontmatter().get("order"));
        assertTrue(pm.body().contains("正文内容"));
    }

    @Test
    void handlesFileWithoutFrontmatter() {
        String raw = "　　这是一份没有元数据的文件。";

        ParsedMarkdown pm = codec.parse(raw);

        assertTrue(pm.frontmatter().isEmpty());
        assertEquals(raw, pm.body());
    }

    @Test
    void handlesBomFromNotepad() {
        // 用记事本打开任意 md 文件、加个空格再保存，就会带上 BOM
        String raw = "﻿---\ntitle: 带 BOM 的文件\n---\n\n正文";

        ParsedMarkdown pm = codec.parse(raw);

        // 没有 stripBom 的话，这里会是 null（正则匹配失败）
        assertEquals("带 BOM 的文件", pm.frontmatter().get("title"));
    }

    @Test
    void handlesCrlfLineEnding() {
        String raw = "---\r\ntitle: Windows 换行\r\n---\r\n\r\n正文";

        ParsedMarkdown pm = codec.parse(raw);

        assertEquals("Windows 换行", pm.frontmatter().get("title"));
    }
}
