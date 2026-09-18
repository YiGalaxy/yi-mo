package com.yimo.storage;

import com.yimo.common.BizException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路径安全测试。这个测试必须一直存在，任何改动都不能让它变红。
 */
class PathGuardTest {

    private final Path root = Path.of("D:/Writing/小说");

    @Test
    void blocksParentDirectoryTraversal() {
        BizException ex = assertThrows(BizException.class,
                () -> PathGuard.resolve(root, "../../Windows/System32/config"));

        assertTrue(ex.getMessage().contains("路径越界"),
                "应提示路径越界，实际: " + ex.getMessage());
    }

    @Test
    void blocksAbsolutePathEscape() {
        // 绝对路径不能绕过检查
        assertThrows(BizException.class,
                () -> PathGuard.resolve(root, "/etc/passwd"));
    }

    @Test
    void allowsNormalRelativePath() {
        Path expected = Path.of("D:/Writing/小说/剑来/07-正文/第001章.md").normalize();
        Path actual = PathGuard.resolve(root, "剑来/07-正文/第001章.md").normalize();

        assertEquals(expected, actual);
    }

    @Test
    void allowsPathWithChineseAndSpaces() {
        Path result = PathGuard.resolve(root, "剑来/03-人物/陈平安 传.md");

        assertTrue(result.startsWith(root.toAbsolutePath().normalize()));
    }

    @Test
    void allowsInnerParentReference() {
        // 中间的回退只要没越出根目录就合法
        Path result = PathGuard.resolve(root, "剑来/07-正文/../03-人物/陈平安.md").normalize();

        assertTrue(result.startsWith(root.toAbsolutePath().normalize()));
        assertTrue(result.toString().endsWith("陈平安.md"));
    }
}
